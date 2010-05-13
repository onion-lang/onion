/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.tools;

import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;

import onion.compiler.CompiledClass;
import onion.compiler.CompilerConfig;
import onion.compiler.InputSource;
import onion.compiler.OnionClassLoader;
import onion.compiler.OnionCompiler;
import onion.compiler.StreamInputSource;

import org.onion_lang.onion.compiler.error.ScriptException;


public class OnionShell {
  private ClassLoader classLoader;
  private String[] classpath;
  
  public OnionShell(ClassLoader classLoader, String[] classpath) {
    this.classLoader = classLoader;
    this.classpath = classpath;
  }
  
  public int run(String script, String fileName, String[] args) {
    OnionCompiler compiler = new OnionCompiler(
      new CompilerConfig(classpath, null, "Shift_JIS", "", 10)
    );
    Thread.currentThread().setContextClassLoader(classLoader);
    CompiledClass[] classes = compiler.compile(
      new InputSource[]{new StreamInputSource(new StringReader(script), fileName)}
    );
    return run(classes, args);
  }
  
  public int run(CompiledClass[] classes, String[] args) {
    try {
      OnionClassLoader loader = new OnionClassLoader(classLoader, classpath, classes);
      Thread.currentThread().setContextClassLoader(loader);
      Method main = findFirstMainMethod(loader, classes);
      if(main == null) {
        return -1;
      }else {
        main.invoke(null, new Object[]{args});
        return 0;
      }
    } catch (ClassNotFoundException e) {
      return -1;
    } catch (IllegalAccessException e) {
      return -1;
    } catch (MalformedURLException e) {
      return -1;
    } catch (InvocationTargetException e) {
      throw new ScriptException(e.getCause());
    }
  }
  
  private Method findFirstMainMethod(OnionClassLoader loader, CompiledClass[] classes) throws ClassNotFoundException {
    for(int i = 0; i < classes.length; i++) {
      String className = classes[i].getClassName();
      Class clazz = Class.forName(className, true, loader);
      try {
        Method main = clazz.getMethod("main", new Class[]{String[].class});
        int modifier = main.getModifiers();
        if((modifier & Modifier.PUBLIC) != 0 && (modifier & Modifier.STATIC) != 0) {
          return main;
        }
      } catch (NoSuchMethodException e1) {/* nothing to do */}
    }
    return null;
  }
}
