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
public class CompilationUnit extends AstNode {
  private String sourceFileName = "<generated>";
  private final ModuleDeclaration moduleDeclaration;
  private final ImportListDeclaration importListDeclaration;
  private final TopLevelElement[] topLevels;

  public CompilationUnit(
    Location loc, ModuleDeclaration moduleDeclaration, 
    ImportListDeclaration importListDeclaration, TopLevelElement[] topLevels) {
    this.moduleDeclaration = moduleDeclaration;
    this.importListDeclaration = importListDeclaration;
    this.topLevels = topLevels;
    setLocation(loc);
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }
  
  public String getSourceFileName() {
    return sourceFileName;
  }
  
  public void setSourceFileName(String sourceFileName) {
    this.sourceFileName = sourceFileName;
  }

  public ModuleDeclaration getModuleDeclaration() {
    return moduleDeclaration;
  }

  public ImportListDeclaration getImportListDeclaration() {
    return importListDeclaration;
  }
  
  public TopLevelElement[] getTopLevels(){
    return topLevels;
  }
}