package com.sodonnel.hadoop.zip;


import java.io.IOException;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class ZipExtractor {
  
  private Boolean extractStructured = false;
  private Boolean extract           = false;
  
  private String zipPath           = null;
  private String matcherRegex      = null;
  
  ZipExtractor(String[] args) throws Exception {
    processArgs(args);
  }
  
  public void run() {
    try {
      ZipReader zr = new ZipReader(zipPath);

      System.out.println("Successfully read zip:");
      System.out.println("  Entries: "+zr.getEntryCount());
      System.out.println("  ZipSize: "+zr.getZipSizeBytes());

      if (matcherRegex == null) {
        // Just list all the files in the zip
        for( ZipEntry entry : zr.entries() ) {
          System.out.println(entry.getFilename()+ " ("+entry.getCompressedSize()+" bytes)");
        }
      }

      if (matcherRegex != null) {
        int matchedFiles = 0;
        ExecutorService threadPool = Executors.newFixedThreadPool(5);
        CompletionService<ExtractFileCallable> pool = new ExecutorCompletionService<ExtractFileCallable>(threadPool);

        Pattern pattern = Pattern.compile(matcherRegex);
        for( ZipEntry entry : zr.entries() ) {
          if (pattern.matcher(entry.getFilename()).matches() == true) {
            if (extract == true) {
              matchedFiles ++;
              pool.submit(new ExtractFileCallable(zr, entry, extractStructured));
            } else {
              System.out.println(entry.getFilename()+ " ("+entry.getCompressedSize()+" bytes)");
            }
          }
        }
        for(int i = 0; i < matchedFiles; i++){
          ExtractFileCallable result = pool.take().get();
          if (result.getExtracted() == true) {
            System.out.println("Extracted "+result.getZipEntry().getFilename());
          } else {
            System.out.println("Failed extracting "+result.getZipEntry().getFilename());
            result.getException().printStackTrace();
          }
        }
        threadPool.shutdown();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  private void processArgs(String[] args) throws Exception {
    if (args.length < 1) {
      throw new Exception("You must pass at least 1 argument for the zip path");
    }
    zipPath = args[0];
    if (args.length >= 2) {
      matcherRegex = args[1];
    }
    extract = searchForBooleanSwitch("--extract", "-e", args);
    extractStructured = searchForBooleanSwitch("--structured", "-s", args);
  }
  
  private Boolean searchForBooleanSwitch(String longSwitch, String shortSwitch, String[] args) {
    for (int i = 2; i<args.length; i++ ) {
      if ((args[i].equals(longSwitch)) || (args[i].equals(shortSwitch))) {
        return true;
      }
    }
    return false;
  }
  
  public static void main(String[] args) throws Exception {
    ZipExtractor ze = new ZipExtractor(args);
    ze.run();
  }

}
