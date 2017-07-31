package com.sodonnel.hadoop.zip;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;

public class ExtractFileCallable implements Callable<ExtractFileCallable> {
  
  ZipReader zip;
  ZipEntry  entry;
  Boolean extracted = false;
  Exception extractException;
  
  ExtractFileCallable(ZipReader zr, ZipEntry ze) {
    zip   = zr;
    entry = ze;
  }

  public Boolean getExtracted() {
    return extracted;
  }

  public Exception getException() {
    return extractException;
  }

  public ZipEntry getZipEntry() {
    return entry;
  }

  public ExtractFileCallable call() throws IOException {
    
    InputStream fileData = null;
    FileOutputStream out = null;
    try {
      fileData = zip.getStreamForEntry(entry);
      out = new FileOutputStream("extracted_"+ new File(entry.getFilename()).getName());
  
      byte[] buffer = new byte[1024];
      int len;
      while ((len = fileData.read(buffer)) != -1) {
          out.write(buffer, 0, len);
      }
      
    } catch (IOException e) {
      extracted = false;
      extractException = e;
      return this;
    } finally {
      fileData.close();
      out.close();
    }
    
    extracted = true;
    return this;
  }

}
