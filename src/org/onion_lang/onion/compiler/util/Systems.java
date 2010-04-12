/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.compiler.util;

/**
 * @author Kota Mizushima
 * Date: 2005/06/26
 */
public class Systems {  
  private Systems() {
  }
  public static String getLineSeparator(){
    return System.getProperty("line.separator");
  }
  public static String getLineSeparator(int count){
    String separator = getLineSeparator();
    StringBuffer separators = new StringBuffer();
    for(int i = 0; i < count; i++){
      separators.append(separator);
    }
    return new String(separators);
  }
  public static String getPathSeparator(){
    return System.getProperty("path.separator");
  }
  public static String getFileSeparator(){
    return System.getProperty("file.separator");
  }
}
