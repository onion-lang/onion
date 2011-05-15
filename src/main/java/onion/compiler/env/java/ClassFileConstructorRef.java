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
public class ClassFileConstructorRef implements IRT.ConstructorRef {
  private int modifier;
  private IRT.ClassTypeRef classType;
  private String name;
  private IRT.TypeRef[] args;

  public ClassFileConstructorRef(
    int modifier, IRT.ClassTypeRef classType, String name, IRT.TypeRef[] args
  ) {
    this.modifier = modifier;
    this.classType = classType;
    this.name = name;
    this.args = (IRT.TypeRef[]) args.clone();
  }
  
  public int modifier(){
    return modifier;
  }

  public IRT.ClassTypeRef affiliation() {
    return classType;
  }

  public String name() {
    return name;
  }

  public IRT.TypeRef[] getArgs() {
    return args;
  }
}
