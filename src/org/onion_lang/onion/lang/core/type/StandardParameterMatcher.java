/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.lang.core.type;

import org.onion_lang.onion.lang.core.IrExpression;

/**
 * @author Kota Mizushima
 * Date: 2005/06/30
 */
public class StandardParameterMatcher implements ParameterMatcher {
  public StandardParameterMatcher() {
  }
  
  public boolean matches(TypeRef[] arguments, IrExpression[] parameters){
    return matchesSub(arguments, parameters);
  }
  
  private static boolean matchesSub(
    TypeRef[] arguments, IrExpression[] parameters){
    if(arguments.length != parameters.length) return false;
    TypeRef[] parameterTypes = new TypeRef[parameters.length];
    for(int i = 0; i < parameters.length; i++){
      parameterTypes[i] = parameters[i].type();
    }
    return matchesSub(arguments, parameterTypes);
  }
  
  private static boolean matchesSub(
    TypeRef[] arguments, TypeRef[] parameterTypes){
    for(int i = 0; i < arguments.length; i++){
      if(!TypeRules.isSuperType(arguments[i], parameterTypes[i])){
        return false;
      }
    }
    return true;
  }
}
