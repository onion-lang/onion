/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.lang.core;

import onion.lang.core.type.ArraySymbol;
import onion.lang.core.type.TypeRef;

/**
 * @author Kota Mizushima
 * Date: 2005/06/21
 */
public class IrArrayRef extends IrExpression {
  private final IrExpression object, index;

  public IrArrayRef(IrExpression target, IrExpression index) {
    this.object = target;
    this.index = index;
  }

  public IrExpression getIndex() {
    return index;
  }
  
  public IrExpression getObject() {
    return object;
  }
  
  public TypeRef type(){
    return ((ArraySymbol)object.type()).getBase();
  }
}