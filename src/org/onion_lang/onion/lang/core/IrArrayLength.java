/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.lang.core;

import org.onion_lang.onion.lang.core.type.*;

/**
 * @author Kota Mizushima
 * Date: 2005/07/06
 */
public class IrArrayLength extends IrExpression {
  private final IrExpression target;

  public IrArrayLength(IrExpression target) {
    this.target = target;
  }

  public IrExpression getTarget() {
    return target;
  }
  
  public TypeRef type(){
    return BasicTypeRef.INT;
  }
}