/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.env.java;

import java.io.*;
import java.io.IOException;
import java.io.InputStream;

import org.apache.bcel.classfile.*;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.util.Repository;
import org.apache.bcel.util.ClassPath;

/**
 * @author Kota Mizushima
 * Date: 2005/06/21
 */
public class ClassFileTable {
  private Repository repository;
  private ClassPath classPath;

  public ClassFileTable(String classPath) {
    this.classPath = new ClassPath(classPath);
    repository = org.apache.bcel.Repository.getRepository();
  }

  /**
   * @param className fully qualified class name
   * @return
   */
  public JavaClass load(String className) {
    try {
      return repository.loadClass(className);
    } catch (ClassNotFoundException e) {
      return add(className);
    }
  }

  private JavaClass add(String className){
    try {
      ClassPath.ClassFile classFile = classPath.getClassFile(className);
      InputStream input = classFile.getInputStream();
      String fileName = new File(classFile.getPath()).getName();
      ClassParser parser = new ClassParser(input, fileName);
      JavaClass javaClass = parser.parse();
      input.close();
      repository.storeClass(javaClass);
      return javaClass;
    } catch (IOException e) {
      return null;
    } catch (ClassFormatException e){
      return null;
    }
  }
}
