/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005-2012, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler


/**
 * @author Kota Mizushima
 * Date: 2005/04/08
 */
case class CompilerConfig(classPath: Seq[String], superClass: String, encoding: String, outputDirectory: String, maxErrorReports: Int) extends AnyRef with Cloneable {
  override def clone: CompilerConfig = {
    CompilerConfig(classPath.toSeq, superClass, encoding, outputDirectory, maxErrorReports)
  }
}