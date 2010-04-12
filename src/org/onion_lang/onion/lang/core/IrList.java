/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.lang.core;

import org.onion_lang.onion.lang.core.type.TypeRef;

/**
 * @author Kota Mizushima
 * Date: 2005/06/21
 */
public class IrList extends IrExpression {
  private final IrExpression[] elements;
  private final TypeRef type;
  
  public IrList(IrExpression[] elements, TypeRef type) {
    this.elements = elements;
    this.type = type;
  } 
  
  public IrExpression[] getElements() { 
    return elements; 
  }
  
  public TypeRef type() { 
    return type; 
  }
}