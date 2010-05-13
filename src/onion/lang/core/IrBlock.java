/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.lang.core;

import java.util.List;

/**
 * @author Kota Mizushima
 * Date: 2005/04/17
 */
public class IrBlock implements IrStatement {  
  private final IrStatement[] statements;

  public IrBlock(IrStatement[] statements) {
    this.statements = statements;
  }
  
  public IrBlock(List statements) {
    this((IrStatement[])statements.toArray(new IrStatement[0]));
  }
  
  public IrBlock(IrStatement statement) {
    this(new IrStatement[]{statement});
  }  
  
  public IrBlock(IrStatement statement1, IrStatement statement2){
    this(new IrStatement[]{statement1, statement2});
  }
  
  public IrBlock(IrStatement statement1, IrStatement statement2, IrStatement statement3){
    this(new IrStatement[]{statement1, statement2, statement3});
  }

  public IrStatement[] getStatements() {
    return statements;
  }
}
