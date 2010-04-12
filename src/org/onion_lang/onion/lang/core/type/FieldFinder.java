/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.lang.core.type;


/**
 * @author Kota Mizushima
 * Date: 2005/07/15
 */
public class FieldFinder {

  public FieldFinder() {
  }

  public FieldSymbol find(ObjectTypeRef target, String name){
    if(target == null) return null;
    FieldSymbol[] fields = target.getFields();
    for (int i = 0; i < fields.length; i++) {
      if(fields[i].getName().equals(name)){
        return fields[i];
      }
    }
    FieldSymbol field = find(target.getSuperClass(), name);
    if(field != null) return field;
    ClassSymbol[] interfaces = target.getInterfaces();
    for(int i = 0; i < interfaces.length; i++){
      field = find(interfaces[i], name);
      if(field != null) return field;
    }
    return null;
  }
}
