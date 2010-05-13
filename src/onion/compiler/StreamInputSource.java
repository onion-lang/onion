/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler;

import java.io.IOException;
import java.io.Reader;

public class StreamInputSource implements InputSource {
  private Reader reader;
  private String name;
  
  public StreamInputSource(Reader reader, String name) {
    this.reader = reader;
    this.name = name;
  }
  
  public Reader openReader() throws IOException {
    return reader;
  }
  
  public String getName() {
    return name;
  }
}
