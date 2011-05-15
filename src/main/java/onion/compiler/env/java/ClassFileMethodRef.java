/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.env.java;

import onion.compiler.IRT;

/**
 * @author Kota Mizushima
 * Date: 2005/06/27
 */
public class ClassFileMethodRef implements IRT.MethodRef {
  private final int modifier;
  private final IRT.ClassTypeRef affiliation;
  private final String name;
  private final IRT.TypeRef[] arguments;
  private final IRT.TypeRef returnType;

  public ClassFileMethodRef(int modifier, IRT.ClassTypeRef affiliation, String name, IRT.TypeRef[] arguments, IRT.TypeRef returnType) {
    this.modifier = modifier;
    this.affiliation = affiliation;
    this.name = name;
    this.arguments = (IRT.TypeRef[]) arguments.clone();
    this.returnType = returnType;
  }
  
  public int modifier(){
    return modifier;
  }

  public IRT.ClassTypeRef affiliation() {
    return affiliation;
  }

  public String name() {
    return name;
  }

  public IRT.TypeRef[] arguments() {
    return arguments;
  }

  public IRT.TypeRef returnType() {
    return returnType;
  }
}
