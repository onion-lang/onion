/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.lang.core.type;


/**
 * @author Kota Mizushima
 * Date: 2005/06/15
 */
public interface MethodSymbol extends MemberSymbol {
  public ClassSymbol getClassType();
  public TypeRef[] getArguments();  
  public TypeRef getReturnType();
}
