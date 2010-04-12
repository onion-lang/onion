/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.compiler;

/**
 * @author Kota Mizushima
 * Date: 2005/04/09
 */
public class CompiledClass {
  private final String className;
  private final String outputPath;
  private final byte[] content;

  public CompiledClass(String className, String outputPath, byte[] content) {
    this.className = className;
    this.outputPath = outputPath;
    this.content = content;
  }
  
  public String getClassName() {
    return className;
  }
  
  public String getOutputPath() {
    return outputPath;
  }
  
  public byte[] getContent() {
    return content;
  }
}
