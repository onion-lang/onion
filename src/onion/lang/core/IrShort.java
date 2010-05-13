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
public class IrShort extends IrExpression {
  private short value;
  public IrShort(short value){
    this.value = value;
  }
  public short getValue() {
    return value;
  }
  
  public TypeRef type() { return BasicTypeRef.SHORT; }
}
