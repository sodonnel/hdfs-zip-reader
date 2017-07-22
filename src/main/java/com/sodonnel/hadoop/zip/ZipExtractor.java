package com.sodonnel.hadoop.zip;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class ZipExtractor {
  
  public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
    
    try {
      ZipReader zr = new ZipReader(args[0]);
      
      System.out.println("Successfully read zip:");
      System.out.println("  Entries: "+zr.getEntryCount());
      System.out.println("  ZipSize: "+zr.getZipSizeBytes());
      
      if (args.length == 1) {
        // Just list all the files in the zip
        for( ZipEntry entry : zr.entries() ) {
          System.out.println(entry.getFilename()+ " ("+entry.getCompressedSize()+" bytes)");
        }
      }
      
      
      if (args.length > 1) {
        int matchedFiles = 0;
        ExecutorService threadPool = Executors.newFixedThreadPool(5);
        CompletionService<Boolean> pool = new ExecutorCompletionService<Boolean>(threadPool);
        
        Pattern pattern = Pattern.compile(args[1]);
        for( ZipEntry entry : zr.entries() ) {

          if (pattern.matcher(entry.getFilename()).matches() == true) {
            System.out.println(entry.getFilename()+ " ("+entry.getCompressedSize()+" bytes)");
            if (args.length == 3) {
              matchedFiles ++;
              pool.submit(new ExtractFileCallable(zr, entry));
            }
          }
        }
        for(int i = 0; i < matchedFiles; i++){
          Boolean result = pool.take().get();
          System.out.println("Completed extracting a file");
        }
        threadPool.shutdown();
      }
    
    } catch (IOException e) {
      System.out.println("Some IO Exeception");
      e.printStackTrace();
    } finally {
      
    }
  }

}
