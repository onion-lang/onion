/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.lang.syntax;

import java.util.ArrayList;
import java.util.List;

import org.onion_lang.onion.lang.syntax.visitor.ASTVisitor;


/**
 * @author Kota Mizushima
 * Date: 2005/04/10
 */
public class AccessSection extends AstNode {
  public static final int PUBLIC_ID = Modifier.PUBLIC;
  public static final int PROTECTED_ID = Modifier.PROTECTED;
  public static final int PRIVATE_ID = Modifier.PRIVATE;
  public static final int DEFAULT_ID = Modifier.PRIVATE;
  
  private final List members = new ArrayList();  
  private final int id;

  public AccessSection(int id) {
    this.id = id;
  }
  
  public static AccessSection PUBLIC(){
    return new AccessSection(PUBLIC_ID);
  }
  
  public static AccessSection PROTECTED(){
    return new AccessSection(PROTECTED_ID);
  }
  
  public static AccessSection PRIVATE(){
    return new AccessSection(PRIVATE_ID);
  }
  
  public static AccessSection DEFAULT(){
    return new AccessSection(DEFAULT_ID);
  }
  
  public int getID(){
    return id;
  }
  
  public void add(MemberDeclaration element){
    members.add(element);
  }
  
  public MemberDeclaration[] getMembers(){
    return (MemberDeclaration[]) members.toArray(new MemberDeclaration[0]);
  }
  
  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }
}
