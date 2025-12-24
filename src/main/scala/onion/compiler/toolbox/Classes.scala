/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.toolbox

import java.util.Arrays
import java.util.Set
import java.util.TreeSet
import onion.compiler.TypedAST

/**
 * @author Kota Mizushima
 *
 */
object Classes {
  def getInterfaceMethods(`type`: TypedAST.ClassType): Set[TypedAST.Method] = {
    val methods: Set[TypedAST.Method] = new TreeSet[TypedAST.Method](new TypedAST.MethodComparator)
    collectInterfaceMethods(`type`, methods)
    methods
  }

  private def collectInterfaceMethods(`type`: TypedAST.ClassType, set: Set[TypedAST.Method]): Unit = {
    val methods = `type`.methods
    set.addAll(Arrays.asList(methods:_*))
    val interfaces: Seq[TypedAST.ClassType] = `type`.interfaces
    for (anInterface <- interfaces) {
      collectInterfaceMethods(anInterface, set)
    }
  }
}
