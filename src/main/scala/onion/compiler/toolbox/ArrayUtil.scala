/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005-2012, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.toolbox

/**
 * @author Kota Mizushima
 *         Date: 2005/06/17
 */
object ArrayUtil {
  def indexOf(element: AnyRef, array: Array[AnyRef]): Int = {
    if (element != null) {
      var i: Int = 0
      while (i < array.length) {
        if (element == array(i)) return i
        i += 1;
        i
      }
    }
    -1
  }

  def equals(array1: Array[AnyRef], array2: Array[AnyRef]): Boolean = {
    if (array1.length != array2.length) return false
    var i: Int = 0
    while (i < array1.length) {
      if (!(array1(i) == array2(i))) return false
      i += 1;
      i
    }
    true
  }
}
