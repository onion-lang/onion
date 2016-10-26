/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005-2012, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.environment

import onion.compiler.IRT

/**
 * @author Kota Mizushima
 */
class ClassFileConstructorRef(val modifier: Int, val classType: IRT.ClassTypeRef, val name: String, val args: Array[IRT.Type]) extends IRT.ConstructorRef {
  def getArgs: Array[IRT.Type] = args
  def affiliation(): IRT.ClassTypeRef = classType
}