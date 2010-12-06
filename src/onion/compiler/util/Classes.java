/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.util;

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

  public static Set getInterfaceMethods(IxCode.ClassTypeRef type){
    Set methods =  new TreeSet(new IxCode.MethodRefComparator());
    collectInterfaceMethods(type, methods);
    return methods;
  }
  
  private static void collectInterfaceMethods(IxCode.ClassTypeRef type, Set set){
    IxCode.MethodRef[] methods = type.methods();
    for(int i = 0; i < methods.length; i++){
      set.add(methods[i]);
    }
    IxCode.ClassTypeRef[] interfaces = type.interfaces();
    for(int i = 0 ; i < interfaces.length; i++){
      collectInterfaceMethods(interfaces[i], set);
    }
  }
}
