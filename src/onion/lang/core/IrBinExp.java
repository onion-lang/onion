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
public class IrBinExp extends IrExpression {
  public interface Constants {
  	int ADD = 0;
  	int SUBTRACT = 1;
  	int MULTIPLY = 2;
  	int DIVIDE = 3;
  	int MOD = 4;
  	
  	//logical operator
  	int LOGICAL_AND = 5;
  	int LOGICAL_OR = 6;
  	
  	//bit operator
  	int BIT_AND = 7;
  	int BIT_OR = 8;
  	int XOR = 9;
  	int BIT_SHIFT_L2 = 10;
  	int BIT_SHIFT_R2 = 11;
  	int BIT_SHIFT_R3 = 12;

  	//comparation operator
  	int LESS_THAN = 13;
  	int GREATER_THAN = 14;
  	int LESS_OR_EQUAL = 15;
  	int GREATER_OR_EQUAL = 16;
  	int EQUAL = 17;
  	int NOT_EQUAL = 18;
    
    //other operator
    int ELVIS = 19;
  }
  
  private final int kind;
  private final IrExpression left, right;
  private final TypeRef type;

  public IrBinExp(
    int kind, TypeRef type, IrExpression left, IrExpression right
  ) {
    this.kind = kind;
    this.left = left;
    this.right = right;
    this.type = type;
  }
  
  public IrExpression getLeft(){
    return left;
  }
  
  public IrExpression getRight(){
    return right;
  }
  
  public int getKind(){
    return kind;
  }
  
  public TypeRef type(){ return type; }
}