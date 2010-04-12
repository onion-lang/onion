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
 * Date: 2005/06/22
 */
public class Strings {
  private Strings() {
  }
  
  public static String join(String[] array, String separator){
    if(array.length == 0) return "";
    StringBuffer buffer = new StringBuffer();
    for (int i = 0; i < array.length - 1; i++) {
      buffer.append(array[i]);
      buffer.append(separator);
    }
    buffer.append(array[array.length - 1]);
    return new String(buffer);
  }
  
  public static String[] append(String[] strings1, String [] strings2){
    String[] newStrings = new String[strings1.length + strings2.length];
    System.arraycopy(strings1, 0, newStrings, 0, strings1.length);
    System.arraycopy(strings2, 0, newStrings, strings1.length, strings2.length);
    return newStrings;
  }
  
  public static String repeat(String source, int times){
    StringBuffer buffer = new StringBuffer();
    for(int i = 0; i < times; i++){
      buffer.append(source);
    }
    return new String(buffer);
  }
}
