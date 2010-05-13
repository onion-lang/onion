/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.lang.core;

import onion.compiler.env.LocalFrame;

import org.onion_lang.onion.lang.core.type.*;

/**
 * @author Kota Mizushima
 * Date: 2005/04/17
 */
public class IrClosure extends IrExpression {
  private ClassSymbol type;
  private MethodSymbol method;
  private IrStatement block;
  private LocalFrame frame;
  
  public IrClosure(ClassSymbol type, MethodSymbol method, IrStatement block) {
    this.type =  type;
    this.method = method;
    this.block = block;
  }
  
  public int getModifier() {
    return method.getModifier();
  }
  
  public ClassSymbol getClassType() {
    return type;
  }
  
  public MethodSymbol getMethod(){
    return method;
  }
  
  public String getName() {
    return method.getName();
  }
  
  public TypeRef[] getArguments() {
    return method.getArguments();
  }
  
  public TypeRef getReturnType() {
    return method.getReturnType();
  }
  
  public IrStatement getBlock() {
    return block;
  }

  public void setFrame(LocalFrame frame){
    this.frame = frame;
  }
  
  public LocalFrame getFrame(){
    return frame;
  }
  
  public TypeRef type() { 
    return type; 
  }
}
