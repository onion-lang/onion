/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler;

import java.io.File;
import java.net.*;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * @author Kota Mizushima
 * Date: 2005/07/19
 */
public class OnionClassLoader extends URLClassLoader {
  public OnionClassLoader(ClassLoader parent, String[] classPath, CompiledClass[] classes) 
    throws MalformedURLException {
    super(getURLs(classPath), parent);
    for(int i = 0; i < classes.length; i++){
      String className = classes[i].getClassName();
      byte[] content = classes[i].getContent();
      defineClass(className, content, 0, content.length);
    }
  }
  
  protected Class findClass(String name) throws ClassNotFoundException {
    return super.findClass(name);
  }
  
  private static URL[] getURLs(String[] classPath) throws MalformedURLException {
    URL[] urls = new URL[classPath.length];
    for(int i = 0; i < classPath.length; i++) {
      urls[i] = new File(classPath[i]).toURI().toURL();
    }
    return urls;
  }
}