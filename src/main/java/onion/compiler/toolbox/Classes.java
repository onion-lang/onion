/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.toolbox;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import onion.compiler.IRT;

/**
 * @author Kota Mizushima
 * Date: 2005/07/12
 */
public class Classes {
  private Classes() {
  }

  public static Set<IRT.MethodRef> getInterfaceMethods(IRT.ClassTypeRef type){
    Set<IRT.MethodRef> methods =  new TreeSet(new IRT.MethodRefComparator());
    collectInterfaceMethods(type, methods);
    return methods;
  }
  
  private static void collectInterfaceMethods(IRT.ClassTypeRef type, Set<IRT.MethodRef> set){
    IRT.MethodRef[] methods = type.methods();
    set.addAll(Arrays.asList(methods));
    IRT.ClassTypeRef[] interfaces = type.interfaces();
    for (IRT.ClassTypeRef anInterface : interfaces) {
      collectInterfaceMethods(anInterface, set);
    }
  }
}
