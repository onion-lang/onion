/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.lang.syntax;


import org.onion_lang.onion.lang.syntax.visitor.ASTVisitor;


/**
 * @author Kota Mizushima
 *  
 */
public class ForeachStatement extends Statement {  
  private final Argument declaration;
  private final Expression collection;
  private final BlockStatement statement;

  public ForeachStatement(Location loc, Argument declaration, Expression collection, BlockStatement statement) {
    this.declaration = declaration;
    this.collection = collection;
    this.statement = statement;
    setLocation(loc);
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }

  public Argument getDeclaration() {
    return declaration;
  }
  
  public Expression getCollection() {
    return collection;
  }

  public BlockStatement getStatement() {
    return statement;
  }
}