/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.lang.core;

import org.onion_lang.onion.lang.core.type.TypeRef;

/**
 * @author Kota Mizushima
 * Date: 2005/06/21
 */
public class IrArraySet extends IrExpression {
  private final IrExpression object, index, value;
  
  public IrArraySet(
    IrExpression target, IrExpression index, IrExpression value
  ) {
    this.object = target;
    this.index = index;
    this.value = value;
  }

  public IrExpression getIndex() {
    return index;
  }
  
  public IrExpression getObject() {
    return object;
  }
  
  public IrExpression getValue() {
    return value;
  }
  
  public TypeRef type(){
    return value.type();
  }
}