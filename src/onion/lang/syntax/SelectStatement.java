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
 *  Represents select statement.
 */
public class SelectStatement extends Statement {
  private final Expression condition;
  private final CaseBranch[] cases;
  private final BlockStatement elseBlock;

  public SelectStatement(Expression condition, CaseBranch[] cases, BlockStatement elseBlock) {
    this.condition = condition;
    this.cases = cases;
    this.elseBlock = elseBlock;
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }
  
  public Expression getCondition() {
    return condition;
  }
  
  public CaseBranch[] getCases(){
    return (CaseBranch[])cases.clone();
  }
  
  public BlockStatement getElseBlock() {
    return elseBlock;
  }
}