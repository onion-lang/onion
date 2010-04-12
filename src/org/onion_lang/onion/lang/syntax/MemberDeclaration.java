/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
/*
 * Created on 2004/12/02
 */
package org.onion_lang.onion.lang.syntax;



/**
 * @author Kota Mizushima
 *  
 */
public abstract class MemberDeclaration extends AstNode {
  private int modifier;
  private String name;

  public MemberDeclaration(String name) {
    this.name = name;
  }

  public int getModifier(){
    return modifier;
  }
  
  public void setModifier(int modifier) {
    this.modifier = modifier;
  }
  
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

}