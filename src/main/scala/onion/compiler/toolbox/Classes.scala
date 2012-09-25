/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005-2012, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.toolbox

import java.util.Arrays
import java.util.Set
import java.util.TreeSet
import onion.compiler.IRT

/**
 * @author Kota Mizushima
 *         Date: 2005/07/12
 */
object Classes {
  def getInterfaceMethods(`type`: IRT.ClassTypeRef): Set[IRT.MethodRef] = {
    val methods: Set[IRT.MethodRef] = new TreeSet[IRT.MethodRef](new IRT.MethodRefComparator)
    collectInterfaceMethods(`type`, methods)
    methods
  }

  private def collectInterfaceMethods(`type`: IRT.ClassTypeRef, set: Set[IRT.MethodRef]) {
    val methods: Array[IRT.MethodRef] = `type`.methods
    set.addAll(Arrays.asList(methods:_*))
    val interfaces: Array[IRT.ClassTypeRef] = `type`.interfaces
    for (anInterface <- interfaces) {
      collectInterfaceMethods(anInterface, set)
    }
  }
}
