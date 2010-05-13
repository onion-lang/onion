/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.lang.core.type;

import java.util.Comparator;


/**
 * @author Kota Mizushima
 * Date: 2005/07/12
 */
public class MethodSymbolComparator implements Comparator {

  public MethodSymbolComparator() {
  }

  public int compare(Object arg0, Object arg1) {
    MethodSymbol m1 = (MethodSymbol)arg0;
    MethodSymbol m2 = (MethodSymbol)arg1;
    int result = m1.getName().compareTo(m2.getName());
    if(result != 0){
      return result;
    }
    TypeRef[] args1 = m1.getArguments();
    TypeRef[] args2 = m2.getArguments();
    result = args1.length - args2.length;
    if(result != 0){
      return result;
    }
    for(int i = 0; i < args1.length; i++){
      if(args1[i] != args2[i]){
        return args1[i].getName().compareTo(args2[i].getName());
      }
    }
    return 0;
  }
}
