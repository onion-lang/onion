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
 * Date: 2005/06/30
 */
public interface ParameterMatcher {
  public boolean matches(TypeRef[] arguments, IrExpression[] parameters);
}
