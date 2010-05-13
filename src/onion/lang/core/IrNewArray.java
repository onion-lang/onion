/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.lang.core;

import onion.lang.core.type.ArraySymbol;
import onion.lang.core.type.TypeRef;

public class IrNewArray extends IrExpression {
  public final ArraySymbol arrayType;
  public final IrExpression[] parameters;
  
  public IrNewArray(ArraySymbol arrayType, IrExpression[] parameters){
    this.arrayType = arrayType;
    this.parameters = parameters;
  }
  
  public TypeRef type() { return arrayType; }
}
