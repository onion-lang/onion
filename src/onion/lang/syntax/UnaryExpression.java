/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.lang.syntax;

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
  
  public String symbol(){
    return symbol; 
  }
  
  public Expression target() {
    return target; 
  }  
}