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
public class IrFieldSet extends IrExpression {
  private final IrExpression object;
  private final FieldSymbol field;
  private final IrExpression value;

  public IrFieldSet(
    IrExpression target, FieldSymbol field, IrExpression value
  ) {
    this.object = target;
    this.field = field;
    this.value = value;
  }
  
  public TypeRef type() { return field.getType(); }

  public IrExpression getObject() { return object; }

  public FieldSymbol getField() { return field; }

  public IrExpression getValue() { return value; }
}
