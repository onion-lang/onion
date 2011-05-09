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
 *
 */
class CompileError(val sourceFile: String, val location: Location, val message: String) {
  def getSourceFile: String = sourceFile
  def getLocation: Location = location
  def getMessage: String =  message
}