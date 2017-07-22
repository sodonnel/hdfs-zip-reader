package com.sodonnel.hadoop.zip;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;

public class ExtractFileCallable implements Callable<Boolean> {
  
  ZipReader zip;
  ZipEntry  entry;
  
  ExtractFileCallable(ZipReader zr, ZipEntry ze) {
    zip   = zr;
    entry = ze;
  }
  
  public Boolean call() throws IOException {
    
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
      return false;
    } finally {
      fileData.close();
      out.close();
    }
    
    
    return true;
  }
  
}
