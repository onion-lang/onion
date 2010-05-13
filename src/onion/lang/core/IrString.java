/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.lang.core;

import onion.lang.core.type.TypeRef;

/**
 * @author Kota Mizushima
 * Date: 2005/06/17
 */
public class IrString extends IrExpression {
  public String value;
  public TypeRef type;
  
  public IrString(String value, TypeRef type){
    this.value = value;
    this.type = type;
  }
  
  public String getValue() { return value; }
  
  public TypeRef type() { return type; }
}
