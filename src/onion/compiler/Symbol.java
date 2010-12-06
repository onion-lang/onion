/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler;

import java.util.HashMap;
import java.util.Map;

/**
 * 
 * @author Kota Mizushima
 *
 */
public final class Symbol {
  private static Map symbols = new HashMap();
  
  public static Symbol intern(String name) {
    Symbol symbol = (Symbol)symbols.get(name);
    if(symbol == null){
      symbol = new Symbol(name);
      symbols.put(name, symbol);
    }
    return symbol;
  }
  
  private String name;
  
  /**
   * Users cannot create Symbol object directly.
   * @param name
   */
  private Symbol(String name) {
    this.name = name;
  }

  /**
   * This method returns this object's name.
   * @return this object's name
   */
  public String getName() {
    return name;
  }
}
