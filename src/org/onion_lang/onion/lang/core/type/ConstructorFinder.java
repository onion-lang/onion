/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.lang.core.type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.onion_lang.onion.lang.core.IrExpression;


/**
 * @author Kota Mizushima
 * Date: 2005/06/30
 */
public class ConstructorFinder {
  private static Comparator sorter = new Comparator(){
    public int compare(Object constructor1, Object constructor2) {
      ConstructorSymbol c1 = (ConstructorSymbol)constructor1;
      ConstructorSymbol c2 = (ConstructorSymbol)constructor2;
      TypeRef[] arg1 = c1.getArgs();
      TypeRef[] arg2 = c2.getArgs();
      int length = arg1.length;
      if(isAllSuperType(arg2, arg1)) return -1;
      if(isAllSuperType(arg1, arg2)) return 1;
      return 0;
    }
  };
  
  private ParameterMatcher matcher;
  
  public ConstructorFinder() {
    this.matcher = new StandardParameterMatcher();
  }
    
  public ConstructorSymbol[] find(ClassSymbol target, IrExpression[] args){
    Set constructors = new TreeSet(new ConstructorSymbolComparator());
    find(constructors, target, args);
    List selected = new ArrayList();
    selected.addAll(constructors);
    Collections.sort(selected, sorter);
    if(selected.size() < 2){
      return (ConstructorSymbol[]) selected.toArray(new ConstructorSymbol[0]);
    }
    ConstructorSymbol constructor1 = (ConstructorSymbol) selected.get(0);
    ConstructorSymbol constructor2 = (ConstructorSymbol) selected.get(1);
    if(isAmbiguous(constructor1, constructor2)){
      return (ConstructorSymbol[]) selected.toArray(new ConstructorSymbol[0]);
    }
    return new ConstructorSymbol[]{constructor1};
  }
  
  private boolean isAmbiguous(ConstructorSymbol constructor1, ConstructorSymbol constructor2){
    return sorter.compare(constructor1, constructor2) >= 0;
  }
  
  private void find(Set constructors, ClassSymbol target, IrExpression[] arguments){
    if(target == null) return;
    ConstructorSymbol[] cs = target.getConstructors();
    for(int i = 0; i < cs.length; i++){
      ConstructorSymbol c = cs[i];
      if(matcher.matches(c.getArgs(), arguments)){
        constructors.add(c);
      }
    }
  }
  
  private static boolean isAllSuperType(
    TypeRef[] arg1, TypeRef[] arg2){
    for(int i = 0; i < arg1.length; i++){
      if(!TypeRules.isSuperType(arg1[i], arg2[i])) return false;
    }
    return true;
  }  
}
