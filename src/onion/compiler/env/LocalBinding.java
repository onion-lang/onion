/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.env;

import onion.compiler.IxCode;


/**
 * @author Kota Mizushima
 */
public class LocalBinding{  
  private final int index;  
  private final IxCode.TypeRef type;
  
  public LocalBinding(int index, IxCode.TypeRef type){
    this.index	= index;
    this.type = type;
  }

  public int getIndex() {
    return index;
  }

  public IxCode.TypeRef getType() {
    return type;
  }
}