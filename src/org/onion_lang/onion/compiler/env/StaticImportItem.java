/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.compiler.env;

/**
 * @author Kota Mizushima
 * Date: 2005/04/15
 */
public class StaticImportItem {
  private String name;
  private boolean fqcn;

  public StaticImportItem(String name, boolean fqcn) {
    this.name = name;
    this.fqcn = fqcn;
  }
  
  /**
   * returns name.
   */
  public String getName() {
    return name;
  }
  
  /**
   * returns whether getName() is FQCN or not.
   * @return
   */
  public boolean isFqcn() {
    return fqcn;
  }
  
  /**
   * matches getName() with name.
   * @param name
   * @return if name is matched, then return true.
   */
  public boolean match(String name) {
    if(fqcn){
      return this.name.equals(name);
    }else{
      return this.name.lastIndexOf(name) == this.name.length() - name.length();
    }
  }
}
