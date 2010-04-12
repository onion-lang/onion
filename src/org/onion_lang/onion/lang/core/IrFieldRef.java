/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.lang.core;

import org.onion_lang.onion.lang.core.type.FieldSymbol;
import org.onion_lang.onion.lang.core.type.TypeRef;

/**
 * @author Kota Mizushima
 * Date: 2005/04/17
 */
public class IrFieldRef extends IrExpression {  
  public IrExpression target;
  public FieldSymbol field;

  public IrFieldRef(IrExpression target, FieldSymbol field) {
    this.target = target;
    this.field = field;
  }
  
  public TypeRef type() { return field.getType(); }
}
