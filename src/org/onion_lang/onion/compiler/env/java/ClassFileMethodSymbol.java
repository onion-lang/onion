/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.compiler.env.java;

import org.onion_lang.onion.lang.core.type.*;

/**
 * @author Kota Mizushima
 * Date: 2005/06/27
 */
public class ClassFileMethodSymbol implements MethodSymbol {
  private int modifier;
  private ClassSymbol classType;  
  private String name;
  private TypeRef[] arguments;
  private TypeRef returnType;

  public ClassFileMethodSymbol(
    int modifier, ClassSymbol classType, String name, 
    TypeRef[] arguments, TypeRef returnType) {
    this.modifier = modifier;
    this.classType = classType;
    this.name = name;
    this.arguments = (TypeRef[]) arguments.clone();
    this.returnType = returnType;
  }
  
  public int getModifier(){
    return modifier;
  }

  public ClassSymbol getClassType() {
    return classType;
  }

  public String getName() {
    return name;
  }

  public TypeRef[] getArguments() {
    return arguments;
  }

  public TypeRef getReturnType() {
    return returnType;
  }
}
