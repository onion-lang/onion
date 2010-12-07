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
  private final int modifier;
  private final IxCode.ClassTypeRef affiliation;
  private final String name;
  private final IxCode.TypeRef[] arguments;
  private final IxCode.TypeRef returnType;

  public ClassFileMethodRef(int modifier, IxCode.ClassTypeRef affiliation, String name, IxCode.TypeRef[] arguments, IxCode.TypeRef returnType) {
    this.modifier = modifier;
    this.affiliation = affiliation;
    this.name = name;
    this.arguments = (IxCode.TypeRef[]) arguments.clone();
    this.returnType = returnType;
  }
  
  public int modifier(){
    return modifier;
  }

  public IxCode.ClassTypeRef affiliation() {
    return affiliation;
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
