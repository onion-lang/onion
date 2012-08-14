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
 * Date: 2005/04/15
 */
class ImportItem(val simpleName: String, val fullyQualifiedName: String) {
  private val onDemand = simpleName == "*"

  /**
   * returns simple name.
   * @return
   */
  def getSimpleName: String =  simpleName

  /**
   * returns fully qualified name.
   * @return
   */
  def getFullyQualifiedName: String = fullyQualifiedName

  /**
   * returns whether this is 'on demand' import or not.
   * @return
   */
  def isOnDemand: Boolean =  onDemand

  /**
   * generate fully qualified name from simple name.
   * @param simpleName
   * @return fqcn.  if simpleName is not matched, then return null.
   */
  def matches(simpleName: String): String = {
    if (onDemand) {
      fullyQualifiedName.replaceAll("\\*", simpleName)
    } else if (this.simpleName == simpleName) {
      fullyQualifiedName
    } else {
      return null
    }
  }

}