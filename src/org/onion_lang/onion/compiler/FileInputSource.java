/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.compiler;

import java.io.IOException;
import java.io.Reader;

import org.onion_lang.onion.compiler.util.Inputs;

public class FileInputSource implements InputSource {
  private Reader reader;
  private String file;
  
  public FileInputSource(String file) {
    this.file = file;
  }
  
  public Reader openReader() throws IOException {
    if(reader == null) reader = Inputs.newReader(file);
    return reader;
  }
  
  public String getName() {
    return file;
  }
}
