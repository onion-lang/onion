/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler;

import onion.lang.syntax.Location;

/**
 * @author Kota Mizushima
 * 
 */
public class CompileError {
  private String sourceFile;
  private Location location;
  private String message;
  
  public CompileError(String sourceFile, Location location, String message) {
    this.sourceFile = sourceFile;
    this.location = location;
    this.message = message;
  }
  
  public String getSourceFile() {
    return sourceFile;
  }
  
  public Location getLocation() {
    return location;
  }
  
  public String getMessage() {
    return message;
  }
}
