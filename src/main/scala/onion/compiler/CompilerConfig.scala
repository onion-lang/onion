/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

import onion.compiler.toolbox.ArrayUtil

/**
 * @author Kota Mizushima
 * Date: 2005/04/08
 */
class CompilerConfig(val classPath: Array[String], val superClass: String, val encoding: String, val outputDirectory: String, val maxErrorReports: Int) extends AnyRef with Cloneable {

  override def clone: AnyRef = {
    new CompilerConfig(classPath.clone.asInstanceOf[Array[String]], superClass, encoding, outputDirectory, maxErrorReports)
  }

  def getClassPath: Array[String] = {
    classPath.clone.asInstanceOf[Array[String]]
  }

  def getSuperClass: String = {
    superClass
  }

  def getEncoding: String = {
    encoding
  }

  def getOutputDirectory: String = {
    outputDirectory
  }

  def getMaxErrorReports: Int = {
    maxErrorReports
  }

  override def equals(obj : Any): Boolean = {
    val another = obj.asInstanceOf[CompilerConfig]
    encoding == another.encoding && superClass == another.superClass && ArrayUtil.equals(classPath.asInstanceOf[Array[AnyRef]], another.classPath.asInstanceOf[Array[AnyRef]]) && outputDirectory == another.outputDirectory && maxErrorReports == another.maxErrorReports
  }
}