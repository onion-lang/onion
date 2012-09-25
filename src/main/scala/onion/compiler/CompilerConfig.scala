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
class CompilerConfig(val classPath: Array[String], val superClass: String, val encoding: String, val outputDirectory: String, val maxErrorReports: Int) extends AnyRef with Cloneable {

  override def clone: CompilerConfig = {
    new CompilerConfig(classPath.clone(), superClass, encoding, outputDirectory, maxErrorReports)
  }

  override def equals(obj : Any): Boolean = {
    val another = obj.asInstanceOf[CompilerConfig]
    (  encoding == another.encoding
    && superClass == another.superClass
    && (classPath.asInstanceOf[Array[AnyRef]] sameElements another.classPath.asInstanceOf[Array[AnyRef]])
    && outputDirectory == another.outputDirectory
    && maxErrorReports == another.maxErrorReports)
  }
}