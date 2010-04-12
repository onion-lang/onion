/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.lang.syntax;

/**
 * @author Kota Mizushima
 * 
 */
public abstract class UnaryExpression extends Expression {
  private final Expression target;
  private final String symbol;

  public UnaryExpression(Expression target, String symbol) {
    this.target = target;
    this.symbol = symbol;
  }
  
  public String getSymbol(){ 
    return symbol; 
  }
  
  public Expression getTarget() { 
    return target; 
  }  
}