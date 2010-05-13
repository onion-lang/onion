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
 * Date: 2005/06/22
 */
public class IrCallStatic extends IrExpression {
  public ObjectTypeRef target;
  public MethodSymbol method;
  public IrExpression[] parameters;
  
  public IrCallStatic(
    ObjectTypeRef target, MethodSymbol method, IrExpression[] parameters) {
    this.target = target;
    this.method = method;
    this.parameters = parameters;
  }
  
  public TypeRef type() { return method.getReturnType(); }
}
