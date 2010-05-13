/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.env;

import onion.lang.core.type.*;

import org.onion_lang.onion.lang.syntax.RawTypeNode;
import org.onion_lang.onion.lang.syntax.TypeSpec;

/**
 * @author Kota Mizushima
 * Date: 2005/06/13
 */
public class NameResolution {
  private ImportList imports;
  private ClassTable table;

  public NameResolution(ImportList imports, ClassTable table) {
    this.imports = imports;
    this.table = table;
  }
    
  public TypeRef resolve(TypeSpec specifier) {
    TypeRef componentType = resolveMain(specifier.getComponent());
    if(specifier.getDimension() > 0){
      return table.loadArray(componentType, specifier.getDimension());
    }else{
      return componentType;
    }
  }
    
  private TypeRef resolveMain(RawTypeNode component) {
    String name = component.name();
    if(component.getKind() == RawTypeNode.BASIC){
      if(name.equals("char")) return BasicTypeRef.CHAR;
      if(name.equals("byte")) return BasicTypeRef.BYTE;
      if(name.equals("short")) return BasicTypeRef.SHORT;
      if(name.equals("int")) return BasicTypeRef.INT;
      if(name.equals("long")) return BasicTypeRef.LONG;
      if(name.equals("float")) return BasicTypeRef.FLOAT;
      if(name.equals("double")) return BasicTypeRef.DOUBLE;
      if(name.equals("boolean")) return BasicTypeRef.BOOLEAN;
      return BasicTypeRef.VOID;
    }else if(component.getKind() == RawTypeNode.NOT_QUALIFIED){
      return forSName(name);
    }else{
      return forQName(name);
    }
  }
  
  private ClassSymbol forQName(String qualifiedName) {
    return table.load(qualifiedName);
  }
  
  private ClassSymbol forSName(String simpleName) {
    for(int i = 0; i < imports.size(); i++){
      String qualifiedName = imports.get(i).match(simpleName);
      if(qualifiedName != null){
        ClassSymbol resolvedSymbol = forQName(qualifiedName);
        if(resolvedSymbol != null) return resolvedSymbol;
      }
    }
    return null;
  }
}
