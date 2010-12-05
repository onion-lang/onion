/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.env;

import onion.compiler.IxCode;
import onion.lang.syntax.RawTypeNode;
import onion.lang.syntax.TypeSpec;


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
    
  public IxCode.TypeRef resolve(TypeSpec specifier) {
    IxCode.TypeRef componentType = resolveMain(specifier.getComponent());
    if(specifier.getDimension() > 0){
      return table.loadArray(componentType, specifier.getDimension());
    }else{
      return componentType;
    }
  }
    
  private IxCode.TypeRef resolveMain(RawTypeNode component) {
    String name = component.name();
    if(component.getKind() == RawTypeNode.BASIC){
      if(name.equals("char")) return IxCode.BasicTypeRef.CHAR;
      if(name.equals("byte")) return IxCode.BasicTypeRef.BYTE;
      if(name.equals("short")) return IxCode.BasicTypeRef.SHORT;
      if(name.equals("int")) return IxCode.BasicTypeRef.INT;
      if(name.equals("long")) return IxCode.BasicTypeRef.LONG;
      if(name.equals("float")) return IxCode.BasicTypeRef.FLOAT;
      if(name.equals("double")) return IxCode.BasicTypeRef.DOUBLE;
      if(name.equals("boolean")) return IxCode.BasicTypeRef.BOOLEAN;
      return IxCode.BasicTypeRef.VOID;
    }else if(component.getKind() == RawTypeNode.NOT_QUALIFIED){
      return forSName(name);
    }else{
      return forQName(name);
    }
  }
  
  private IxCode.ClassTypeRef forQName(String qualifiedName) {
    return table.load(qualifiedName);
  }
  
  private IxCode.ClassTypeRef forSName(String simpleName) {
    for(int i = 0; i < imports.size(); i++){
      String qualifiedName = imports.get(i).match(simpleName);
      if(qualifiedName != null){
        IxCode.ClassTypeRef resolvedSymbol = forQName(qualifiedName);
        if(resolvedSymbol != null) return resolvedSymbol;
      }
    }
    return null;
  }
}
