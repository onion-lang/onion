/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.lang.core;

import onion.lang.core.type.*;

/**
 * @author Kota Mizushima
 * Date: 2005/04/17
 */
public class IrStaticFieldSet extends IrExpression {
  public ObjectTypeRef target;
  public FieldSymbol field;
  public IrExpression value;

  public IrStaticFieldSet(
    ObjectTypeRef target, FieldSymbol field, IrExpression value){
    this.target = target;
    this.field = field;
    this.value = value;
  }
  
  public TypeRef type() { return field.getType(); }
}
