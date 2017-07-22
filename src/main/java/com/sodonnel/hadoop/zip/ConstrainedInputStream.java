package com.sodonnel.hadoop.zip;

import java.io.IOException;
import java.io.InputStream;

public class ConstrainedInputStream extends InputStream {
  private final InputStream decorated;
  private long length;

  public ConstrainedInputStream(InputStream decorated, long length) {
    this.decorated = decorated;
    this.length = length;
  }

  @Override public int read() throws IOException {
    return (length-- <= 0) ? -1 : decorated.read();
  }
  
  @Override public void close() throws IOException {
    decorated.close();
  }

  // TODO: override other methods if you feel it's necessary
  // optionally, extend FilterInputStream instead
}
