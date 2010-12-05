/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.env.java;

import onion.compiler.IxCode;

/**
 * @author Kota Mizushima
 * Date: 2005/06/27
 */
public class ClassFileMethodRef implements IxCode.MethodRef {
  private int modifier;
  private IxCode.ClassTypeRef classType;
  private String name;
  private IxCode.TypeRef[] arguments;
  private IxCode.TypeRef returnType;

  public ClassFileMethodRef(
    int modifier, IxCode.ClassTypeRef classType, String name,
    IxCode.TypeRef[] arguments, IxCode.TypeRef returnType) {
    this.modifier = modifier;
    this.classType = classType;
    this.name = name;
    this.arguments = (IxCode.TypeRef[]) arguments.clone();
    this.returnType = returnType;
  }
  
  public int modifier(){
    return modifier;
  }

  public IxCode.ClassTypeRef affiliation() {
    return classType;
  }

  public String name() {
    return name;
  }

  public IxCode.TypeRef[] arguments() {
    return arguments;
  }

  public IxCode.TypeRef returnType() {
    return returnType;
  }
}
