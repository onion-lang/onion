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
    RawTypeNode component = specifier.getComponent();
    String name = component.name();
    IxCode.TypeRef resolvedType;
    if(component.kind() == RawTypeNode.BASIC){
      resolvedType =
        name.equals("char") ? IxCode.BasicTypeRef.CHAR :
        name.equals("byte") ? IxCode.BasicTypeRef.BYTE :
        name.equals("short") ? IxCode.BasicTypeRef.SHORT :
        name.equals("int") ? IxCode.BasicTypeRef.INT :
        name.equals("long") ? IxCode.BasicTypeRef.LONG :
        name.equals("float") ? IxCode.BasicTypeRef.FLOAT :
        name.equals("double") ? IxCode.BasicTypeRef.DOUBLE :
        name.equals("boolean") ? IxCode.BasicTypeRef.BOOLEAN :
                                  IxCode.BasicTypeRef.VOID;
    }else if(component.kind() == RawTypeNode.NOT_QUALIFIED){
      resolvedType = forName(name, false);
    }else{
      resolvedType = forName(name, true);
    }
    IxCode.TypeRef componentType = resolvedType;
    if(specifier.getDimension() > 0){
      return table.loadArray(componentType, specifier.getDimension());
    }else{
      return componentType;
    }
  }

  private IxCode.ClassTypeRef forName(String name, boolean qualified) {
    if(qualified) {
      return table.load(name);
    }else {
      for(int i = 0; i < imports.size(); i++){
        String qualifiedName = imports.get(i).match(name);
        if(qualifiedName != null){
          IxCode.ClassTypeRef resolvedSymbol = forName(qualifiedName, true);
          if(resolvedSymbol != null) return resolvedSymbol;
        }
      }
      return null;
    }
  }
}
