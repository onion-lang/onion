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
public class BlockStatement extends Statement {
  private final Statement[] statements;

  public BlockStatement(Location loc, Statement[] statements) {
    this.statements = statements;
    setLocation(loc);
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }

  public Statement[] getStatements() {
    return statements;
  }
  
  public Statement getStatement(int index){
    return statements[index];
  }
  
  public int size(){
    return statements.length;
  } 
}