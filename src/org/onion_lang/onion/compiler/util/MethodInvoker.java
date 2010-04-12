/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.compiler.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author Kota Mizushima
 * Date: 2005/09/15
 */
public class MethodInvoker {
  private MethodInvoker() {}
  
  public static Object call(Object target, String name, Object[] args)
    throws InvocationException {    
    try {
      return getMethod(target.getClass(), name, args).invoke(target, args);
    } catch (NoSuchMethodException e) {
      throw new InvocationException(e);
    } catch (IllegalArgumentException e) {
      throw new InvocationException(e);
    } catch (IllegalAccessException e) {
      throw new InvocationException(e);
    } catch (InvocationTargetException e) {
      throw new InvocationException(e);
    }
  }
  
  public static Object callStatic(Class target, String name, Object[] args) 
  	throws InvocationException{
    try {
      return getMethod(target, name, args).invoke(null, args);
    } catch (NoSuchMethodException e) {
      throw new InvocationException(e);
    } catch (IllegalArgumentException e) {
      throw new InvocationException(e);
    } catch (IllegalAccessException e) {
      throw new InvocationException(e);
    } catch (InvocationTargetException e) {
      throw new InvocationException(e);
    }

  }
  
  private static Method getMethod(Class target, String name, Object[] args)
  	throws NoSuchMethodException {
    Class[] argsClasses = new Class[args.length];
    for(int i = 0; i < args.length; i++) {
      argsClasses[i] = args[i].getClass();
    }
    return target.getMethod(name, argsClasses);    
  }
}
