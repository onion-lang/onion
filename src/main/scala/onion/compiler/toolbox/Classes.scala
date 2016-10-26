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
 *
 */
object Classes {
  def getInterfaceMethods(`type`: IRT.ClassType): Set[IRT.Method] = {
    val methods: Set[IRT.Method] = new TreeSet[IRT.Method](new IRT.MethodComparator)
    collectInterfaceMethods(`type`, methods)
    methods
  }

  private def collectInterfaceMethods(`type`: IRT.ClassType, set: Set[IRT.Method]) {
    val methods: Array[IRT.Method] = `type`.methods
    set.addAll(Arrays.asList(methods:_*))
    val interfaces: Array[IRT.ClassType] = `type`.interfaces
    for (anInterface <- interfaces) {
      collectInterfaceMethods(anInterface, set)
    }
  }
}
