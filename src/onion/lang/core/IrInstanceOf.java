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
 * Date: 2005/04/17
 */
public class IrInstanceOf extends IrExpression {    
  public IrExpression target;
  public TypeRef checked;

  public IrInstanceOf(IrExpression target, TypeRef checked){
    this.target = target;
    this.checked = checked;
  }
  
  public TypeRef getCheckType() {
    return checked;
  }
  
  public IrExpression getTarget() {
    return target;
  }
  
  public TypeRef type() { return BasicTypeRef.BOOLEAN; }
}