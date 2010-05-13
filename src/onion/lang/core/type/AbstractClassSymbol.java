/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.lang.core.type;

import onion.lang.core.IrExpression;

/**
 * @author Kota Mizushima
 * Date: 2005/07/15
 */
public abstract class AbstractClassSymbol extends AbstractObjectSymbol implements ClassSymbol {
  private ConstructorFinder constructorFinder;
  
  public AbstractClassSymbol() {
    constructorFinder = new ConstructorFinder();
  }
  
  public ConstructorSymbol[] findConstructor(IrExpression[] params){
    return constructorFinder.find(this, params);
  }
  
  public boolean isClassType() {
    return true;
  }

  public boolean isArrayType() {
    return false;
  }
}
