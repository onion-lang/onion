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

public class SymbolTable {
  private Map table;
  
  public SymbolTable() {
    this.table = new HashMap();
  }
  
  public void put(Symbol key, Object value) {
    table.put(key, value);
  }
  
  public Object get(Symbol key) {
    return table.get(key);
  }
  
  public boolean containsKey(Symbol key) {
    return table.containsKey(key);
  }
}
