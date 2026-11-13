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
  // Every unresolved name is tried against every import, so these strings were rebuilt
  // -- take/append/mkString over the segment list -- on each attempt. They depend only on
  // the item, so they are built once.
  // The default package's on-demand import is the single segment `*`: its prefix is the
  // empty string, not `.` -- a leading dot made every default-package class unresolvable.
  private val onDemandPrefix: String =
    if (!isOnDemand || fqcn.isEmpty) null
    else if (fqcn.length == 1) ""
    else fqcn.take(fqcn.length - 1).mkString("", ".", ".")
  private val fullName: String = fqcn.mkString(".")

  def matches(simpleName: String): Option[String] = {
    if (isOnDemand) {
      if (onDemandPrefix == null) None
      else Some(onDemandPrefix + simpleName)
    } else if (this.simpleName == simpleName) {
      Some(fullName)
    } else {
      None
    }
  }

}
