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
public class IrCallSuper extends IrExpression {
  private final IrExpression target;
  private final MethodSymbol method;
  private final IrExpression[] params;
  
  public IrCallSuper(IrExpression target, MethodSymbol method, IrExpression[] params) {
    this.target = target;
    this.method = method;
    this.params = params;
  }
  
  public TypeRef type() { 
    return method.getReturnType(); 
  }

  public IrExpression getTarget() {
    return target;
  }

  public MethodSymbol getMethod() {
    return method;
  }

  public IrExpression[] getParams() {
    return params;
  }
}
