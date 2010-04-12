/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.lang.core;

import org.onion_lang.onion.compiler.env.ClosureLocalBinding;

/**
 * @author Kota Mizushima
 * Date: 2005/06/17
 */
public class IrTry implements IrStatement {
  public IrStatement tryStatement;
  public ClosureLocalBinding[] catchTypes;
  public IrStatement[] catchStatements;
  public IrTry(
    IrStatement tryStatement, ClosureLocalBinding[] catchTypes,
    IrStatement[] catchStatements){
    this.tryStatement = tryStatement;
    this.catchTypes = catchTypes;
    this.catchStatements = catchStatements;
  }
}
