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
case class ImportItem(simpleName : String, fqcn: Seq[String]) {
  val isOnDemand: Boolean  = simpleName == "*"

  /**
   * generate fully qualified name from simple name.
   * @param simpleName
   * @return fqcn.  if simpleName is not matched, then return null.
   */
  def matches(simpleName: String): Option[String] = {
    if (isOnDemand) {
      if(fqcn.length == 0) None
      else Some(fqcn.take(fqcn.length - 1).appended(simpleName).mkString("."))
    } else if (this.simpleName == simpleName) {
      Some(fqcn.mkString((".")))
    } else {
      None
    }
  }

}
