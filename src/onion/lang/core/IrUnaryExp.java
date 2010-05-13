/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.lang.core;

import onion.lang.core.type.TypeRef;

/**
 * @author Kota Mizushima
 * Date: 2005/04/17
 */
public class IrUnaryExp extends IrExpression {
  public interface Constants {
  	int PLUS = 0;
  	int MINUS = 1;
  	int NOT = 2;
  	int BIT_NOT = 3;
  }
  
  private int kind;
  private IrExpression operand;
  private TypeRef type;

  public IrUnaryExp(
    int kind, TypeRef type, IrExpression operand) {
    this.kind = kind;
    this.operand = operand;
    this.type = type;
  }
  
  public int getKind(){
    return kind;
  }
  
  public IrExpression getOperand(){
    return operand;
  }
  
  public TypeRef type() { return type; }
}