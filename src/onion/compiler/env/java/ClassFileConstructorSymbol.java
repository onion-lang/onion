/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.env.java;

import onion.lang.core.type.*;

/**
 * @author Kota Mizushima
 * Date: 2005/06/27
 */
public class ClassFileConstructorSymbol implements ConstructorSymbol {
  private int modifier;
  private ClassSymbol classType; 
  private String name;
  private TypeRef[] args;

  public ClassFileConstructorSymbol(
    int modifier, ClassSymbol classType, String name, TypeRef[] args
  ) {
    this.modifier = modifier;
    this.classType = classType;
    this.name = name;
    this.args = (TypeRef[]) args.clone();
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

  public TypeRef[] getArgs() {
    return args;
  }
}
