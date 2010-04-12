/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.compiler.env;

import java.util.ArrayList;
import java.util.List;

/**
 * A type safe import list.
 * @author Kota Mizushima
 * 
 */
public class StaticImportList {
  private List items = new ArrayList();

  public StaticImportList(){
  }
  
  public void add(StaticImportItem item){
    items.add(item);
  }
  
  public String get(int index){
    return (String)items.get(index);
  }
  
  public StaticImportItem[] getItems(){
    return (StaticImportItem[])items.toArray(new StaticImportItem[0]);
  }
  
  public int size(){
    return items.size();
  }
}
