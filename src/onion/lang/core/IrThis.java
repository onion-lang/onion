/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.lang.core;

import onion.lang.core.type.ClassSymbol;
import onion.lang.core.type.TypeRef;

/**
 * @author Kota Mizushima
 * Date: 2005/06/17
 */
public class IrThis extends IrExpression {
  private ClassSymbol classType;
  
  public IrThis(ClassSymbol classType){
    this.classType = classType;
  }
  
  public TypeRef type() { return classType; }
}
