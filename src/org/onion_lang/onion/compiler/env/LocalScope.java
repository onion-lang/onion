/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.compiler.env;

import java.util.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.onion_lang.onion.compiler.util.Systems;

/**
 * a Local variable table.
 * @author Kota Mizushima
 */
public class LocalScope{  
  private LocalScope parent;  
  private Map bindings = new HashMap();

  public LocalScope(LocalScope parent){
    this.parent = parent;
  }
  
  /**
   * Gets registered binding objects.
   * @return Set object which element is LocalBinding object
   */
  public Set entries(){
    Set entries = new HashSet();
    entries.addAll(bindings.values());
    return entries;
  }
  
  /**
   * Tests if this scope contains entry for the given name.
   * @param name
   * @return true if this scope has entry, false otherwise
   */
  public boolean contains(String name){
    return bindings.containsKey(name);
  }
  
  /**
   * Registers binding object to this scope for the given name.
   * @param name
   * @param binding
   * @return true if already putted for given name, false otherwise
   */
  public boolean put(String name, LocalBinding binding){
    if(bindings.containsKey(name)){
      return true;
    }
    bindings.put(name, binding);
    return false;
  }
  
  /**
   * Gets the registered binding object from this scope for given name.
   * @param name
   * @return the LocalBinding object if registered, null otherwise
   */
  public LocalBinding get(String name){
    return (LocalBinding)bindings.get(name);
  }
  
  /**
   * Finds the registered binding object from this scope and its ancestors 
   * for given name.
   * @param name
   * @return the LocalBinding object if found, null otherwise
   */
  public LocalBinding lookup(String name){
    LocalScope table = this;
    while(table != null){
      if(table.contains(name)){
        return (LocalBinding)table.get(name);
      }
      table = table.getParent();
    }
    return null;
  }
  
  public String toString(){
    String separator = Systems.getLineSeparator();
    StringBuffer string = new StringBuffer();    
    string.append("[");
    string.append(separator);
    for(Iterator i = bindings.keySet().iterator(); i.hasNext(); ){
      String name = (String)i.next();
      string.append("  ");
      string.append(name);
      string.append(":");
      string.append(((LocalBinding)bindings.get(name)).getType());
      string.append(separator);
    }
    string.append("]");    
    return new String(string);
  }

  public LocalScope getParent() {
    return parent;
  }
}
