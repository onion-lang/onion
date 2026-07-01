/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

/**
 * @author Kota Mizushima
 *
 */
class StaticImportItem(
  val name: String,
  val fqcn: Boolean,
  val methodName: String = null
) {

  /**
   * returns name.
   */
  def getName: String = name

  /**
   * returns whether name() is FQCN or not.
   * @return
   */
  def isFqcn: Boolean = fqcn

  /**
   * Returns the imported method name, or null if the whole class is imported.
   */
  def getMethodName: String = methodName

  /**
   * matches name() with name.
   * @param name
   * @return if name is matched, then return true.
   */
  def `match`(name: String): Boolean = {
    if (fqcn) {
      this.name.equals(name)
    }
    else {
      this.name.lastIndexOf(name) == this.name.length - name.length
    }
  }

  /**
   * Returns true if this import makes the given static method available.
   */
  def importsMethod(methodName: String): Boolean = {
    if (this.methodName == null) true
    else this.methodName == methodName
  }
}