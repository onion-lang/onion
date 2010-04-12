/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.lang.syntax;

/**
 * @author Kota Mizushima
 * 
 */
public final class Location {
  private int line;
  private int column;

  public Location(int line, int column) {
    this.line = line;
    this.column = column;
  }

  public int getColumn() {
    return column;
  }

  public int getLine() {
    return line;
  }
}
