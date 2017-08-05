package com.sodonnel.hadoop.zip;

import java.io.IOException;

import org.apache.hadoop.fs.FSDataInputStream;

public class ZipEntry {
  
  String filename = null;
  String comment  = null;
  
  short compression = 0;
  long  crc;
  long  compressedSize;
  long  uncompressedSize;
  long  entryOffset;
  short filenameLength;
  short commentLength;
  short extraLength;
  
  
  ZipEntry(FSDataInputStream is) throws IOException {
    // Expect the stream to be position right at the start of an entry
    // magic number, if it is not, this will fail.
    final int DIRENTRYMAGIC = 0x02014b50;

 //   for (int i =0; i < 30; i++) {
 //     System.out.format(+i+": %02X \n", is.read());
 //     //short val = Short.reverseBytes(is.readShort());
 //     //System.out.println("Iternation "+ i + " value: "+val);
 //   }
    
    if (Integer.reverseBytes(is.readInt()) != DIRENTRYMAGIC) {
      throw new IOException("The start of the DirectoryEntry does not have the correct magic number");
    }
    
    // First 3 fields Version Made By, Version Needed to Extract, General Purpose Bit
    // Don't care about these so skip them
    is.skipBytes(6);

    // The value 8 is deflate, the value 0 mean no compression
    compression = Short.reverseBytes(is.readShort());
    
    // Next 4 bytes are File modification time (2), file mod date (2)
    // Don't care about these so skip them
    is.skipBytes(4);
    crc = (long)Integer.reverseBytes(is.readInt()) & 0xffffffffL;

    // With original ZIP, the compressed and uncompressed sizes are 4 bytes (max 1GB)
    // With ZIP64 the can be 8 byes, so for now cast them into longs
    compressedSize = (long)Integer.reverseBytes(is.readInt())  & 0xffffffffL;
    uncompressedSize = (long)Integer.reverseBytes(is.readInt()) & 0xffffffffL;
    
    filenameLength = Short.reverseBytes(is.readShort());
    extraLength = Short.reverseBytes(is.readShort());
    commentLength = Short.reverseBytes(is.readShort());
    // Disk number where file starts (2), Internal File Attrs (2), External file attrs (4)
    is.skipBytes(8);
    
    entryOffset = (long)Integer.reverseBytes(is.readInt()) & 0xffffffffL;
    filename = readVariableString(is, filenameLength);
    
    /*
       We need to hand the extra field, which will contain the entry offsets
       for Zip64 entries, where their offset is greater than 4GB into the file
       The spec states that the extra file should contain a value for a given
       field ONLY if the field in the normal header is 0xffffffff, ie all ones.
       The field order is guaranteed, but not all fields must be present.

       (ZIP64) 0x0001     2 bytes    Tag for this "extra" block type
        Size       2 bytes    Size of this "extra" block
        Original
        Size       8 bytes    Original uncompressed file size
        Compressed
        Size       8 bytes    Size of compressed data
        Relative Header
        Offset     8 bytes    Offset of local header record
        Disk Start
        Number     4 bytes    Number of the disk on which
                              this file starts
    */
    if (mustHaveZip64Extra() == true) {
      if (extraLength < 4) {
        throw new IOException("The entry must have a Zip64 extra field, but it is too short");
      }
      if (Short.reverseBytes(is.readShort()) != 0x0001) {
        throw new IOException("The entry must have a Zip64 extra field, but the header byte is not 0x0001");
      }
      is.skip(2);
      int remainingBytes = extraLength - 4;
      if ((int)uncompressedSize == 0xffffffff) {
        uncompressedSize = Long.reverseBytes(is.readLong());
        remainingBytes -= 8;
      }
      if ((int)compressedSize == 0xffffffff) {
        compressedSize = Long.reverseBytes(is.readLong());
        remainingBytes -= 8;
      }
      if ((int)entryOffset == 0xffffffff) {
        entryOffset = Long.reverseBytes(is.readLong());
        remainingBytes -= 8;
      }
      is.skip(remainingBytes);
    } else {
      is.skipBytes(extraLength);
    }
    comment = readVariableString(is, commentLength);
  }

  private Boolean mustHaveZip64Extra() {
    if (
        ((int)uncompressedSize == 0xffffffff) ||
        ((int)compressedSize == 0xffffffff)   ||
        ((int)entryOffset == 0xffffffff)
       ) {
      return true;
    } else {
      return false;
    }
  }
  
  public short getCompression() {
    return compression;
  }
  
  public long getCrc() {
    return crc;
  }
  
  public long getCompressedSize() {
    return compressedSize;
  }

  public long getUnCompressedSize() {
    return uncompressedSize;
  }
  
  public long getEntryOffset() {
    return entryOffset;
  }
  
  public String getFilename() {
    return filename;
  }
  
  public String getComment() {
    return comment;
  }

  
  public void display() {
    System.out.println("CRC: "+ crc);
    System.out.println("Compression: "+ compression);
    System.out.println("Compressed length: "+ compressedSize);
    System.out.println("Uncompression length: "+ uncompressedSize);
    System.out.println("Filename length: "+ filenameLength);
    System.out.println("Extra length: "+ extraLength);
    System.out.println("Comment length: "+ commentLength);
    System.out.println("Filename: "+ filename);
    System.out.println("Entry offset: "+ entryOffset);
  }
  
  
  
  private String readVariableString(FSDataInputStream is, int len) throws IOException {
    if (len == 0) {
      return null;
    }
    byte[] buff = new byte[len];
    is.readFully(buff, 0, len);
    return new String(buff, 0, len);
  }

}
