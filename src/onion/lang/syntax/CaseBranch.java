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
 * Date: 2005/04/11
 */
public class CaseBranch extends AstNode {  
  private final Expression[] expressions;  
  private final BlockStatement block;

  public CaseBranch(Expression[] expressions, BlockStatement block) {
    this.expressions = expressions;
    this.block = block;
  }
  
  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }

  public Expression[] getExpressions(){
    return expressions;
  }
  
  public BlockStatement getBlock(){
    return block;
  }
}
