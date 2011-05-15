/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

/**
 * @author Kota Mizushima
 */
class LocalBinding(val index: Int, val `type`: IRT.TypeRef) {
  def getIndex: Int =  return index
  def getType: IRT.TypeRef = `type`
}