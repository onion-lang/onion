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
public class IrLocalSet extends IrExpression {
  private final int frame;
  private final int index;
  private final IrExpression value;
  private final TypeRef type;

  public IrLocalSet(int frame, int index, TypeRef type, IrExpression value){
    this.frame = frame;
    this.index = index;
    this.value = value;
    this.type = type;
  }
  
  public IrLocalSet(ClosureLocalBinding bind, IrExpression value){
    this.frame = bind.getFrame();
    this.index = bind.getIndex();
    this.type = bind.getType();
    this.value = value;
  }

  public int getFrame() {
    return frame;
  }

  public int getIndex() {
    return index;
  }

  public IrExpression getValue() {
    return value;
  }

  public TypeRef type() { return type; }
}
