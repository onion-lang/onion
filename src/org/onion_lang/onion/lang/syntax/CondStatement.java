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
public class CondStatement extends Statement {
  private final Expression[] conditions;
  private final BlockStatement[] blocks;
  private final BlockStatement elseBlock; 

  public CondStatement(
    Expression[] conditions, BlockStatement[] blocks, BlockStatement elseBlock
  ) {
    this.conditions = conditions;
    this.blocks = blocks;
    this.elseBlock = elseBlock;
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }  

  public BlockStatement getElseBlock() {
    return elseBlock;
  }
  
  public BlockStatement getBlock(int index){
    return blocks[index];
  }
  
  public Expression getCondition(int index){
    return conditions[index];
  }
  
  public int size(){
    return blocks.length;
  }
}