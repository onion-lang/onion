/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.compiler.util;

/**
 * @author Kota Mizushima
 * Date: 2005/07/07
 */
public class SymbolGenerator {
  private final String prefix;
  private int count;
  
  public SymbolGenerator(String prefix) {
    this.prefix = prefix;
  }

  public String getPrefix(){
    return prefix;
  }
  
  public int getCount(){
    return count;
  }
  
  public String generate(){
    String newSymbol = prefix + count;
    count++;
    return newSymbol;
  }
}
