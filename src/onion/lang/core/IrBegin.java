/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.lang.core;

import java.util.List;

import onion.lang.core.type.TypeRef;

/**
 * @author Kota Mizushima
 * Date: 2005/04/17
 */
public class IrBegin extends IrExpression { 
  private final IrExpression[] expressions;

  public IrBegin(IrExpression[] expressions) {
    this.expressions = expressions;
  }
  
  public IrBegin(List expressions) {
    this((IrExpression[])expressions.toArray(new IrExpression[0]));
  }
  
  public IrBegin(IrExpression expression) {
    this(new IrExpression[]{expression});
  }  
  
  public IrBegin(IrExpression expression1, IrExpression expression2){
    this(new IrExpression[]{expression1, expression2});
  }
  
  public IrBegin(IrExpression expression1, IrExpression expression2, IrExpression expression3){
    this(new IrExpression[]{expression1, expression2, expression3});
  }

  public IrExpression[] getExpressions() {
    return expressions;
  }
  
  public TypeRef type() {
    return expressions[expressions.length - 1].type();
  }
}
