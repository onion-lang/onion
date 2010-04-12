/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.lang.core;

import org.onion_lang.onion.compiler.env.LocalFrame;
import org.onion_lang.onion.lang.core.type.*;

/**
 * @author Kota Mizushima
 * Date: 2005/04/17
 */
public class IrMethod implements IrNode, MethodSymbol {
  private int modifier;  
  private ClassSymbol classType;
  private String name;
  private TypeRef[] arguments;
  private IrBlock block;
  private TypeRef returnType;
  private boolean closure;
  private LocalFrame frame;

  public IrMethod(
    int modifier, ClassSymbol classType, String name, TypeRef[] arguments,
    TypeRef returnType, IrBlock block){
    this.modifier = modifier;
    this.classType = classType;
    this.name = name;
    this.arguments = arguments;
    this.returnType = returnType;
    this.block = block;
  }
  
  public int getModifier() {
    return modifier;
  }
  
  public ClassSymbol getClassType() {
    return classType;
  }
  
  public String getName() {
    return name;
  }
  
  public TypeRef[] getArguments() {
    return arguments;
  }
  
  public TypeRef getReturnType(){
    return returnType;
  }
  
  public IrBlock getBlock() {
    return block;
  }
  
  public void setBlock(IrBlock block){
    this.block = block;
  }
  
  public void setClosure(boolean closure){
    this.closure = closure;
  }
  
  public boolean hasClosure(){
    return closure;
  }
  
  public void setFrame(LocalFrame frame) {
    this.frame = frame;
  }
  
  public LocalFrame getFrame() {
    return frame;
  }
}