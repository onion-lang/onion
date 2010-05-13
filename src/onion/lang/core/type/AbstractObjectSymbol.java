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
public abstract class AbstractObjectSymbol implements ObjectTypeRef {
  private MethodFinder methodFinder;
  private FieldFinder fieldFinder;
  
  public AbstractObjectSymbol() {
    methodFinder = new MethodFinder();
    fieldFinder = new FieldFinder();
  }

  public FieldSymbol findField(String name) {
    return fieldFinder.find(this, name);
  }

  public MethodSymbol[] findMethod(String name, IrExpression[] params) {
    return methodFinder.find(this, name, params);
  }

  public boolean isBasicType() {
    return false;
  }

  public boolean isNullType() {
    return false;
  }

  public boolean isObjectType() {
    return true;
  }
}
