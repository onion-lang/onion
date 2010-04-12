/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.lang.core;

import org.onion_lang.onion.lang.core.type.ClassSymbol;
import org.onion_lang.onion.lang.core.type.TypeRef;

/**
 * @author Kota Mizushima
 * Date: 2005/04/17
 */
public class IrSuper implements IrNode {  
  private ClassSymbol classType;
  private TypeRef[] arguments;
  private IrExpression[] expressions;

  public IrSuper(
    ClassSymbol classType, TypeRef[] arguments, IrExpression[] expressions){
    this.classType = classType;
    this.arguments = arguments;
    this.expressions = expressions;
  }
  
  public ClassSymbol getClassType() {
    return classType;
  }
  
  public TypeRef[] getArguments() {
    return arguments;
  }
  
  public IrExpression[] getExpressions() {
    return expressions;
  }
  
}
