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
public class IrLong extends IrExpression {
  private final long value;
  
  public IrLong(long value){
    this.value = value;
  }
  
  public long getValue() { return value; }
  
  public TypeRef type() { return BasicTypeRef.LONG; }
}
