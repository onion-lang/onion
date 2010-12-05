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
public class ClassFileConstructorRef implements IxCode.ConstructorRef {
  private int modifier;
  private IxCode.ClassTypeRef classType;
  private String name;
  private IxCode.TypeRef[] args;

  public ClassFileConstructorRef(
    int modifier, IxCode.ClassTypeRef classType, String name, IxCode.TypeRef[] args
  ) {
    this.modifier = modifier;
    this.classType = classType;
    this.name = name;
    this.args = (IxCode.TypeRef[]) args.clone();
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

  public IxCode.TypeRef[] getArgs() {
    return args;
  }
}
