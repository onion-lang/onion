/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.lang.core;

import onion.lang.core.type.BasicTypeRef;
import onion.lang.core.type.TypeRef;

/**
 * @author Kota Mizushima
 * Date: 2005/06/17
 */
public class IrBool extends IrExpression {
  private final boolean value;
  
  public IrBool(boolean value){
    this.value = value;
  }
  
  public boolean getValue() {
    return value;
  }
  
  public TypeRef type(){ 
    return BasicTypeRef.BOOLEAN; 
  }
}
