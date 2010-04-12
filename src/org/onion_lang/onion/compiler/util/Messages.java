/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.compiler.util;

import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * @author Kota Mizushima
 * Date: 2005/06/17
 */
public class Messages {
  private static final ResourceBundle ERROR_MESSAGES;
  
  static {
    ERROR_MESSAGES = ResourceBundle.getBundle("resources.errorMessage");
  }
  
  private Messages() {
  }

  public static String get(String property){
    return ERROR_MESSAGES.getString(property);
  }
  
  public static String get(String property, Object[] arguments){
    return MessageFormat.format(get(property), arguments);
  }
  
  public static String get(String property, Object arg1){
    return MessageFormat.format(get(property), new Object[]{arg1});
  }
  
  public static String get(String property, Object arg1, Object arg2){
    return MessageFormat.format(get(property), new Object[]{arg1, arg2});
  }
  
  public static String get(String property, Object arg1, Object arg2, Object arg3){
    return MessageFormat.format(get(property), new Object[]{arg1 ,arg2, arg3});
  }
}
