/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.compiler.util;

import java.io.File;

/**
 * @author Kota Mizushima
 * Date: 2005/06/17
 */
public class Paths {
  private Paths(){}
  public static String getName(String path){
    return new File(path).getName();
  }
  public static String cutExtension(String path){
    String name = getName(path);
    return name.substring(0, name.lastIndexOf('.'));
  }
}
