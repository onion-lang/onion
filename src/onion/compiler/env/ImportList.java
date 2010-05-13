/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.env;

import java.util.ArrayList;
import java.util.List;

/**
 * A type safe import list.
 * @author Kota Mizushima
 * 
 */
public class ImportList {  
  private List items = new ArrayList();

  public ImportList(){
  }
  
  public void add(ImportItem item){
    items.add(item);
  }
  
  public ImportItem get(int index){
    return (ImportItem)items.get(index);
  }
  
  public ImportItem[] getItems(){
    return (ImportItem[])items.toArray(new ImportItem[0]);
  }
  
  public int size(){
    return items.size();
  }
}
