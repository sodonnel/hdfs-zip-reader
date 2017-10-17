package com.sodonnel.hadoop.zip;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;

public class ExtractFileCallable implements Callable<ExtractFileCallable> {
  
  ZipReader zip;
  ZipEntry  entry;
  Boolean   extractToOriginalPath = false;
  Boolean extracted = false;
  Exception extractException;
  
  ExtractFileCallable(ZipReader zr, ZipEntry ze, Boolean originalLocation) {
    zip   = zr;
    entry = ze;
    extractToOriginalPath = originalLocation;
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
      
      File f = new File(entry.getFilename());
      String dest = "extracted_"+f.getName();
      
      if (extractToOriginalPath == true) {
        File pf = f.getParentFile();
        if (pf != null) {
          pf.mkdirs();
        }
        dest = entry.getFilename();
      }
      out = new FileOutputStream(dest);
      
      byte[] buffer = new byte[1024];
      int len;
      while ((len = fileData.read(buffer)) != -1) {
          out.write(buffer, 0, len);
      }
      
    } catch (IOException e) {
      extracted = false;
      extractException = e;
      e.printStackTrace();
      return this;
    } finally {
      fileData.close();
      out.close();
    }
    
    extracted = true;
    return this;
  }

}
