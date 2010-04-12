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
 *  
 */
public class ImportListDeclaration extends AstNode {
  private List names = new ArrayList();

  private List fqcns = new ArrayList();

  private int size;

  public ImportListDeclaration() {
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }

  public String getName(int index) {
    return (String) names.get(index);
  }

  public String getFQCN(int index) {
    return (String) fqcns.get(index);
  }

  public int size() {
    return size;
  }

  public void add(String name, String fqcn) {
    names.add(name);
    fqcns.add(fqcn);
    size++;
  }

}