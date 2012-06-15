/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.toolbox;

/**
 * @author Kota Mizushima
 * Date: 2005/06/17
 */
public class ArrayUtil {
  private ArrayUtil() {
  }
  
  public static int indexOf(Object element, Object[] array){
    if(element != null) {
      for(int i = 0; i < array.length; i++){
        if(element.equals(array[i])) return i;
      }
    }
    return -1;
  }
  
  public static boolean equals(Object[] array1, Object[] array2){
    if(array1.length != array2.length) return false;
    for(int i = 0; i < array1.length; i++){
      if(!array1[i].equals(array2[i])) return false;
    }
    return true;
  }
}
