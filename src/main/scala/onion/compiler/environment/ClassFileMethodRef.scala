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
 *
 */
class ClassFileMethodRef(val modifier: Int, val affiliation: IRT.ClassType, val name: String, val arguments_ : Array[IRT.Type], val returnType: IRT.Type) extends IRT.Method {
  val arguments: Array[IRT.Type] = arguments_.clone()
}