/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.lang.core.type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import onion.lang.core.IrExpression;


/**
 * @author Kota Mizushima
 * Date: 2005/06/30
 */
public class MethodFinder {
  private static Comparator sorter = new Comparator(){
    public int compare(Object method1, Object method2) {
      MethodSymbol m1 = (MethodSymbol)method1;
      MethodSymbol m2 = (MethodSymbol)method2;
      TypeRef[] arg1 = m1.getArguments();
      TypeRef[] arg2 = m2.getArguments();
      int length = arg1.length;
      if(isAllSuperType(arg2, arg1)) return -1;
      if(isAllSuperType(arg1, arg2)) return 1;
      return 0;
    }
  };
    
  private ParameterMatcher matcher;
  
  public MethodFinder() {
    this.matcher = new StandardParameterMatcher();
  }
    
  public MethodSymbol[] find(ObjectTypeRef target, String name, IrExpression[] arguments){
    Set methods = new TreeSet(new MethodSymbolComparator());
    find(methods, target, name, arguments);
    List selectedMethods = new ArrayList();
    selectedMethods.addAll(methods);
    Collections.sort(selectedMethods, sorter);
    if(selectedMethods.size() < 2){
      return (MethodSymbol[]) selectedMethods.toArray(new MethodSymbol[0]);
    }
    MethodSymbol method1 = (MethodSymbol) selectedMethods.get(0);
    MethodSymbol method2 = (MethodSymbol) selectedMethods.get(1);
    if(isAmbiguous(method1, method2)){
      return (MethodSymbol[]) selectedMethods.toArray(new MethodSymbol[0]);
    }
    return new MethodSymbol[]{method1};
  }
  
  public boolean isAmbiguous(MethodSymbol method1, MethodSymbol method2){
    return sorter.compare(method1, method2) >= 0;
  }
  
  private void find(
    Set methods, ObjectTypeRef target, String name, IrExpression[] params){
    if(target == null) return;
    MethodSymbol[] ms = target.getMethods();
    for(int i = 0; i < ms.length; i++){
      MethodSymbol m = ms[i];
      if(m.getName().equals(name) && matcher.matches(m.getArguments(), params)){
        methods.add(m);
      }
    }
    ClassSymbol superClass = target.getSuperClass();
    find(methods, superClass, name, params);
    ClassSymbol[] interfaces = target.getInterfaces();
    for(int i = 0; i < interfaces.length; i++){
      find(methods, interfaces[i], name, params);
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
