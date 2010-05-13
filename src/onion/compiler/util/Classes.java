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

import onion.lang.core.type.*;

/**
 * @author Kota Mizushima
 * Date: 2005/07/12
 */
public class Classes {
  private Classes() {
  }

  public static Set getInterfaceMethods(ClassSymbol type){
    Set methods =  new TreeSet(new MethodSymbolComparator());
    collectInterfaceMethods(type, methods);
    return methods;
  }
  
  private static void collectInterfaceMethods(ClassSymbol type, Set set){
    MethodSymbol[] methods = type.getMethods();
    for(int i = 0; i < methods.length; i++){
      set.add(methods[i]);
    }
    ClassSymbol[] interfaces = type.getInterfaces();
    for(int i = 0 ; i < interfaces.length; i++){
      collectInterfaceMethods(interfaces[i], set);
    }
  }
}
