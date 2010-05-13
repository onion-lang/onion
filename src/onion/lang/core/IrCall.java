/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.lang.core;

import onion.lang.core.type.MethodSymbol;
import onion.lang.core.type.TypeRef;

/**
 * @author Kota Mizushima
 * Date: 2005/06/22
 */
public class IrCall extends IrExpression {
  public final IrExpression target;
  public final MethodSymbol method;
  public final IrExpression[] parameters;
  
  public IrCall(
    IrExpression target, MethodSymbol method, IrExpression[] parameters) {
    this.target = target;
    this.method = method;
    this.parameters = parameters;
  }
  
  public TypeRef type() { return method.getReturnType(); }
}
