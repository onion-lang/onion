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
 * Date: 2005/04/17
 */
public interface ClassSymbol extends ObjectTypeRef {
  ConstructorSymbol[] getConstructors();
  ConstructorSymbol[] findConstructor(IrExpression[] params);
}