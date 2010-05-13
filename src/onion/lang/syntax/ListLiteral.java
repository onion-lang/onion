/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.lang.syntax;

import onion.lang.syntax.visitor.ASTVisitor;

/**
 * @author Kota Mizushima
 *  
 */
public class ListLiteral extends Literal {
  private Expression[] expressions;

  public ListLiteral(Location loc, Expression[] expressions) {
    this.expressions = expressions;
    setLocation(loc);
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }
  
  public Expression getExpression(int index){
    return expressions[index];
  }
  
  public int size(){
    return expressions.length;
  }
}