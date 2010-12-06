/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.lang.syntax;

import onion.compiler.Location;
import onion.lang.syntax.visitor.ASTVisitor;

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