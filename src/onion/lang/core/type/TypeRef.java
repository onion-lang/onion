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
 * Date: 2005/04/17
 */
public interface TypeRef {
  String getName();
  boolean isBasicType();
  boolean isClassType();
  boolean isNullType();
  boolean isArrayType();
  boolean isObjectType();
}