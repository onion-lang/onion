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
public class TryStatement extends Statement {
  private final BlockStatement tryBlock;
  private final BlockStatement[] recBlocks;
  private final Argument[] arguments;
  private final BlockStatement finBlock;

  public TryStatement(
    Location loc, BlockStatement tryBlock, BlockStatement[] recBlocks, 
    Argument[] arguments, BlockStatement finBlock
  ) {
    this.tryBlock = tryBlock;
    this.recBlocks = recBlocks;
    this.arguments = arguments;
    this.finBlock = finBlock;
    setLocation(loc);
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }

  /**
   * @return tryBlock
   */
  public BlockStatement getTryBlock() {
    return tryBlock;
  }

  /**
   * @return recBlocks
   */
  public BlockStatement[] getRecBlocks() {
    return recBlocks;
  }

  /**
   * @return arguments
   */
  public Argument[] getArguments() {
    return arguments;
  }

  /**
   * @return finBlock
   */
  public BlockStatement getFinBlock() {
    return finBlock;
  }
}