/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
/*
 * Created on 2004/12/02
 */
package onion.lang.syntax;


import onion.lang.syntax.visitor.ASTVisitor;


/**
 * @author Kota Mizushima
 *  
 */
public class AdditionAssignment extends BinaryExpression {

  public AdditionAssignment(Expression left, Expression right) {
    super("+=", left, right);
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }

}