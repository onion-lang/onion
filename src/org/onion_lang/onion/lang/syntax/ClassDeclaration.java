/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.lang.syntax;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


import org.onion_lang.onion.lang.syntax.visitor.ASTVisitor;

/**
 * @author Kota Mizushima
 *  
 */
public class ClassDeclaration extends TypeDeclaration {  
  private final TypeSpec superClass;  
  private final TypeSpec[] interfaces;  
  private final AccessSection defaultSection;  
  private final List sections = new ArrayList();
  
  public ClassDeclaration(int modifier, String name, TypeSpec superClass, TypeSpec[] interfaces, AccessSection defaultSection, AccessSection[] sections) {
    super(modifier, name);
    this.superClass = superClass;
    this.interfaces = (TypeSpec[]) interfaces.clone();
    this.defaultSection = defaultSection;
    this.sections.addAll(Arrays.asList(sections));
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }

  public TypeSpec getSuperClass() {
    return superClass;
  }
  
  public TypeSpec[] getInterfaces(){
    return (TypeSpec[])interfaces.clone();
  }
  
  public AccessSection getDefaultSection(){
    return defaultSection;
  }
  
  public AccessSection[] getSections(){
    return (AccessSection[])sections.toArray(new AccessSection[0]);
  }
  
  public void addSection(AccessSection section){
    sections.add(section);
  }  
}