/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.lang.core;

import onion.compiler.env.LocalFrame;
import onion.lang.core.type.*;

import org.onion_lang.onion.lang.syntax.Modifier;

/**
 * @author Kota Mizushima
 * Date: 2005/04/17
 */
public class IrConstructor implements IrNode, ConstructorSymbol {
  private int modifier;
  private ClassSymbol classType;
  private TypeRef[] arguments;
  private IrBlock block;
  private IrSuper superInitializer;
  private LocalFrame frame;
  
  public IrConstructor(
    int modifier, ClassSymbol classType, 
    TypeRef[] arguments, IrBlock block, IrSuper superInitializer
  ) {
    this.modifier = modifier;
    this.classType = classType;
    this.arguments = arguments;
    this.block = block;
    this.superInitializer = superInitializer;
  }
  
  public static IrConstructor newDefaultConstructor(ClassSymbol type) {
    IrBlock block = new IrBlock(new IrReturn(null));
    IrSuper init = new IrSuper(type.getSuperClass(), new TypeRef[0], new IrExpression[0]);
    IrConstructor node =  new IrConstructor(Modifier.PUBLIC, type, new TypeRef[0], block, init);
    node.setFrame(new LocalFrame(null));
    return node;
  }
     
  public String getName() {
    return "new";
  }

  public TypeRef[] getArgs() {
    return arguments;
  }
  
  public ClassSymbol getClassType() {
    return classType;
  }
  
  public int getModifier() {
    return modifier;
  }
  
  public IrSuper getSuperInitializer() {
    return superInitializer;
  }
  
  public void setSuperInitializer(IrSuper superInitializer) {
    this.superInitializer = superInitializer;
  }
  
  public void setBlock(IrBlock block) {
    this.block = block;
  }
  
  public IrBlock getBlock() {
    return block;
  }

  public void setFrame(LocalFrame frame) {
    this.frame = frame;
  }
  
  public LocalFrame getFrame() {
    return frame;
  }
}
