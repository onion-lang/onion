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
 * Date: 2005/04/15
 */
class StaticImportItem(val name: String, val fqcn: Boolean) {

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
}