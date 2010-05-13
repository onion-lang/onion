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
 * Date: 2005/06/22
 */
public interface ObjectTypeRef extends TypeRef {
  boolean isInterface();
  int getModifier();
  ClassSymbol getSuperClass();
  ClassSymbol[] getInterfaces();
  MethodSymbol[] getMethods();
  FieldSymbol[] getFields();
  FieldSymbol findField(String name);
  MethodSymbol[] findMethod(String name, IrExpression[] params);
}