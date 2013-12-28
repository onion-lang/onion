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
 *
 */
case class ImportItem(simpleName : String, fqcn: String) {
  val isOnDemand: Boolean  = simpleName == "*"

  /**
   * generate fully qualified name from simple name.
   * @param simpleName
   * @return fqcn.  if simpleName is not matched, then return null.
   */
  def matches(simpleName: String): String = {
    if (isOnDemand) {
      fqcn.replaceAll("\\*", simpleName)
    } else if (this.simpleName == simpleName) {
      fqcn
    } else {
      null
    }
  }

}
