/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.env;

/**
 * @author Kota Mizushima
 * Date: 2005/04/15
 */
public class ImportItem {
  private String simpleName;
  private String fullyQualifiedName;
  private boolean onDemand;

  public ImportItem(String simpleName, String fullyQualifiedName) {
    this.simpleName = simpleName;
    this.fullyQualifiedName = fullyQualifiedName;
    this.onDemand = simpleName.equals("*");
  }
  
  /**
   * returns simple name.
   * @return
   */
  public String getSimpleName() {
    return simpleName;
  }
  
  /**
   * returns fully qualified name.
   * @return
   */
  public String getFullyQualifiedName() {
    return fullyQualifiedName;
  }
  
  /**
   * returns whether this is 'on demand' import or not.
   * @return
   */
  public boolean isOnDemand() {
    return onDemand;
  }
  
  /**
   * generate fully qualified name from simple name.
   * @param simpleName
   * @return fqcn.  if simpleName is not matched, then return null.
   */
  public String match(String simpleName) {
    if(onDemand){
      return fullyQualifiedName.replaceAll("\\*", simpleName);
    }else if(this.simpleName.equals(simpleName)){
      return fullyQualifiedName;
    }else{
      return null;
    }
  }
}
