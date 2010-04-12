/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.lang.core;

import org.onion_lang.onion.lang.core.type.ConstructorSymbol;
import org.onion_lang.onion.lang.core.type.TypeRef;

/**
 * @author Kota Mizushima
 * Date: 2005/06/22
 */
public class IrNew extends IrExpression {
  public final ConstructorSymbol constructor;
  public final IrExpression[] parameters;
  
  public IrNew(ConstructorSymbol constructor, IrExpression[] parameters){
    this.constructor = constructor;
    this.parameters = parameters;
  }
  
  public TypeRef type() { return constructor.getClassType(); }
}
