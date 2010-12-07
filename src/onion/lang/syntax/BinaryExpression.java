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
public abstract class BinaryExpression extends Expression {
  private String symbol;
  private final Expression left, right;

  public BinaryExpression(String symbol, Expression left, Expression right) {
    this.symbol = symbol;
    this.left = left;
    this.right = right;
  }
  
  public String symbol(){
    return symbol;
  }

  public Expression left() {
    return left;
  }

  public Expression right() {
    return right;
  }
}