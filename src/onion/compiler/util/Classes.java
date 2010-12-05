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

  public static Set getInterfaceMethods(IxCode.ClassSymbol type){
    Set methods =  new TreeSet(new IxCode.MethodSymbolComparator());
    collectInterfaceMethods(type, methods);
    return methods;
  }
  
  private static void collectInterfaceMethods(IxCode.ClassSymbol type, Set set){
    IxCode.MethodSymbol[] methods = type.getMethods();
    for(int i = 0; i < methods.length; i++){
      set.add(methods[i]);
    }
    IxCode.ClassSymbol[] interfaces = type.getInterfaces();
    for(int i = 0 ; i < interfaces.length; i++){
      collectInterfaceMethods(interfaces[i], set);
    }
  }
}
