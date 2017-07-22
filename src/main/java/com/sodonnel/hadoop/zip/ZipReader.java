package com.sodonnel.hadoop.zip;

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipInputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

public class ZipReader {
  
  long EOCD_MAX = 1024*64 + 22;
  long EOCD_MIN = 22;
  
  int zipEntries;
  long centralDirectoryOffset;
  
  LinkedHashMap<String, ZipEntry> entries = new LinkedHashMap<String, ZipEntry>();
  
  Path zipPath;
  Configuration HDFSConf;
  
  long zipLengthBytes;
  
  ZipReader(String path) throws IOException {
    zipPath = new Path(path);
    HDFSConf = new Configuration();
    validateZip();
    readEOCD();
    readEntries();
  }
  
  public Collection<ZipEntry> entries() {
    return entries.values();
  }
  
  public ZipEntry getEntry(String name) {
    return entries.get(name);
  }
  
  public int getEntryCount() {
    return zipEntries;
  }
  
  public long getZipSizeBytes() {
    return zipLengthBytes;
  }
  
  
  /**
   * The Central Directory is written at the end of the file. It contains a variable length comment that
   * is at most 64K, plus the entry itself is a further 22 bytes before the comment field
   * 
   * The EOCD entry has a magic number 0x06054b50 which can be used to locate it.
   * 
   * This method will read the final 64K + 22 bytes from the end of the file and 
   * then read through it looking for the magic number. 
   * @throws IOException 
   * 
   * TODO - ZIP64 adds an extra footer, need to check for and read it too
   * 
   */
  public void readEOCD() throws IOException {
    
    FileSystem fs = zipPath.getFileSystem(HDFSConf);

    FSDataInputStream is = fs.open(zipPath);
    
    final int ENDHEADERMAGIC = 0x06054b50;
        
    is.seek(zipLengthBytes - EOCD_MAX - 1);
    byte[] buff = new byte[(int)EOCD_MAX];
    is.readFully(buff, 0, (int)EOCD_MAX);
    for (int i = 0; i < EOCD_MAX; i++ ) {  
      int x = java.nio.ByteBuffer.wrap(buff, i, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt();
      if (x == ENDHEADERMAGIC) {
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(buff, i + 4, 16).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        
        short diskNum = bb.getShort();
        short dirDiskNum = bb.getShort();
        short dirEntriesOnDisk = bb.getShort();
        zipEntries = bb.getShort();
        int dirSize = bb.getInt();
        // In ZIPs this is an unsigned int, but Java has signed ints
        // To we must read this into a long and mask it to remove the sign
        centralDirectoryOffset = bb.getInt()  & 0xFFFFFFFFL;
        
        is.close();
        break;
      }
    }
    
  }
  
  private void validateZip() {
    try {
      FileSystem fs = zipPath.getFileSystem(HDFSConf);
      FileStatus status = fs.getFileStatus(zipPath);
      
      zipLengthBytes = status.getLen();
      
      if (zipLengthBytes < EOCD_MIN) {
        throw new IOException(zipLengthBytes +" is too small to be a valid zip");
      }
    } catch (IOException e) {
      System.out.println(e);
    }
  }
  
  public InputStream getStreamForEntry(ZipEntry entry) throws IOException {
    FileSystem fs = zipPath.getFileSystem(HDFSConf);
    FSDataInputStream is = fs.open(zipPath);
    is.seek(entry.getEntryOffset());
    
    /*
     * We have positioned the stream at the start of the ZipEntry in the file,
     * but we don't know where the data starts. To read the data, we need to
     * consume the fileHeader entry fields, one of which will contain the data length.
     * 
     * However, even after we know the compressed length, we need to read the rest
     * of the header fields to position the stream at the start of the data.
     * 
     * TODO - What does a zip contain in compressed / uncompressed length if there is no compression?
     * 
     */
    
    int entryMagicNumber = 0x04034b50;
    if (Integer.reverseBytes(is.readInt()) != entryMagicNumber) {
      throw new IOException("File Header entry does not start with correct magic number");
    }
    
    // Version (2), General purpose (2)
    is.skip(4);
    
    short compression = Short.reverseBytes(is.readShort());
    
    // File mod time (2), file mod date (2)
    is.skip(4);
    
    int crc = Integer.reverseBytes(is.readInt());
    long compressedSizeZip64 = 0;
    int compressedSize = Integer.reverseBytes(is.readInt());
    int uncompressedSize = Integer.reverseBytes(is.readInt());
    
    short fileNameLength = Short.reverseBytes(is.readShort());
    short extraLength = Short.reverseBytes(is.readShort());
    is.skip(fileNameLength);
    

    if (compressedSize == -1) {
      /*
       * This indicates a ZIP64 archive, and the actual length is stored in the extra field
       * 
       * Format is 2 bytes (0x0001) | 2 bytes (size) | 8 bytes (original size) | 8 bytes (compressed size) 
       */
      byte[] buff = new byte[extraLength];
      is.readFully(buff, 0, extraLength);
      java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(buff, 0, extraLength).order(java.nio.ByteOrder.LITTLE_ENDIAN);
      if (bb.getShort() != 0x0001) {
        throw new IOException("Entry Header suggests Zip64, but no Zip64 extra data found");
      }
      bb.getShort(); // skip size
      bb.getLong();  // skip original length
      compressedSizeZip64 = bb.getLong();
    } else {
      is.skip(extraLength);
    }
    
    /*
     * At this point, the stream is position at the start of the data, so we can read it
     * 
     * Limitation - only deflate compression or no compression is supported. If the data 
     * was deflated, then we wrap the stream in an Inflater
     * 
     * TODO - What is the file in the zip is zero length?
     * 
     */
    ConstrainedInputStream cis = new ConstrainedInputStream(is, compressedSize == -1 ? compressedSizeZip64 : compressedSize); 
    
    if (compression == 8) {
      /*
       * The default Inflater expects header bytes at the start of the stream, and with 
       * Zip files that header is not there. For that reason we must pass an Inflater(true)
       * which tells the inflater not to check for the header bytes.
       * 
       */
      return new InflaterInputStream(cis, new Inflater(true));   
    } else {
      return cis;
    }
  }
    
  private void readEntries() throws IOException {
    FileSystem fs = zipPath.getFileSystem(HDFSConf);

    FSDataInputStream is = fs.open(zipPath);
    is.seek(centralDirectoryOffset);
    for (int i=0; i<zipEntries; i++) {
      ZipEntry entry = new ZipEntry(is);
      entries.put(entry.getFilename(), entry);
    }
  }    

}
