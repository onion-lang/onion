/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.lang.core.type;

import java.util.Comparator;

/**
 * @author Kota Mizushima
 * Date: 2005/07/12
 */
public class ConstructorSymbolComparator implements Comparator {

  public ConstructorSymbolComparator() {
  }

  public int compare(Object arg0, Object arg1) {
    ConstructorSymbol c1 = (ConstructorSymbol)arg0;
    ConstructorSymbol c2 = (ConstructorSymbol)arg1;
    int result;
    TypeRef[] args1 = c1.getArgs();
    TypeRef[] args2 = c2.getArgs();
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
