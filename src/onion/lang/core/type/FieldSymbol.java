/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.lang.core.type;


/**
 * @author Kota Mizushima
 * Date: 2005/06/15
 */
public interface FieldSymbol extends MemberSymbol {
  public int getModifier();
  public ClassSymbol getClassType();
  public TypeRef getType();
}
