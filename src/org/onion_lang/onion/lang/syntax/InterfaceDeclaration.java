/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.lang.syntax;

import org.onion_lang.onion.lang.syntax.visitor.ASTVisitor;

/**
 * @author Kota Mizushima
 *  
 */
public class InterfaceDeclaration extends TypeDeclaration {  
  private TypeSpec[] interfaces;
  private InterfaceMethodDeclaration[] declarations;

  /**
   * @param parent
   */
  public InterfaceDeclaration(int modifier, String name, TypeSpec[] interfaces, InterfaceMethodDeclaration[] declarations) {
    super(modifier, name);
    this.declarations = declarations;
    this.interfaces = interfaces;
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }

  /**
   * @return Returns the method declarations.
   */
  public InterfaceMethodDeclaration[] getDeclarations() {
    return (InterfaceMethodDeclaration[])declarations.clone();
  }

  /**
   * 
   * @return Rerurns the super interfaces.
   */
  public TypeSpec[] getInterfaces() {
    return (TypeSpec[]) interfaces.clone();
  } 
}