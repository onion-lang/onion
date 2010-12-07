/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.util;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import onion.compiler.IxCode;

/**
 * @author Kota Mizushima
 * Date: 2005/07/12
 */
public class Classes {
  private Classes() {
  }

  public static Set<IxCode.MethodRef> getInterfaceMethods(IxCode.ClassTypeRef type){
    Set<IxCode.MethodRef> methods =  new TreeSet(new IxCode.MethodRefComparator());
    collectInterfaceMethods(type, methods);
    return methods;
  }
  
  private static void collectInterfaceMethods(IxCode.ClassTypeRef type, Set<IxCode.MethodRef> set){
    IxCode.MethodRef[] methods = type.methods();
    set.addAll(Arrays.asList(methods));
    IxCode.ClassTypeRef[] interfaces = type.interfaces();
    for (IxCode.ClassTypeRef anInterface : interfaces) {
      collectInterfaceMethods(anInterface, set);
    }
  }
}
