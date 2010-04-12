/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.lang.core;

import org.onion_lang.onion.compiler.env.ClosureLocalBinding;
import org.onion_lang.onion.lang.core.type.TypeRef;

/**
 * @author Kota Mizushima
 * Date: 2005/04/17
 */
public class IrLocalRef extends IrExpression {
  private int frame;
  private int index;
  private TypeRef type;

  public IrLocalRef(ClosureLocalBinding bind) {
    this.frame = bind.getFrame();
    this.index = bind.getIndex();
    this.type = bind.getType();
  }
  
  public IrLocalRef(int frame, int index, TypeRef type){
    this.frame = frame;
    this.index = index;
    this.type = type;
  }
  
  public int frame(){ return frame; }
  
  public int index(){ return index; }
  
  public TypeRef type() { return type; }
}
