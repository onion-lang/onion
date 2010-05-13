/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.tools;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

import onion.compiler.util.*;
import onion.tools.OnionCompilerFrontend;


import junit.framework.TestCase;

/**
 * @author Kota Mizushima
 * Date: 2005/04/09
 */
public class TestOnionCompilerFrontend extends TestCase {
  private static final String TEST_DIRECTORY = "testTemp";
  private OnionCompilerFrontend testTarget;
  private static FileFilter srcFilter = new FileFilter(){
    public boolean accept(File path) {
      return path.isFile() && path.getName().endsWith(".on");
    }
  };
  private static FileFilter dstFilter = new FileFilter(){
    public boolean accept(File path) {
      return path.isFile() && path.getName().endsWith(".class");
    }
  };

  public static void main(String[] args) {
    junit.textui.TestRunner.run(TestOnionCompilerFrontend.class);
  }

  protected void setUp() throws Exception {
    new File(TEST_DIRECTORY).mkdirs();
    testTarget = new OnionCompilerFrontend();
  }

  protected void tearDown() throws Exception {
    File[] files = new File(TEST_DIRECTORY).listFiles();
    for(int i = 0; i < files.length; i++){
      files[i].delete();
    }
    new File(TEST_DIRECTORY).delete();
  }

  public TestOnionCompilerFrontend(String arg0) {
    super(arg0);
  }
  
  private static String[] getExampleSources(String exampleDir) 
    throws IOException{
    File[] files = new File(exampleDir).listFiles(srcFilter);
    String[] fileNames = new String[files.length];
    for (int i = 0; i < fileNames.length; i++) {
      fileNames[i] = files[i].getCanonicalPath();
    }
    return fileNames;
  }

  public void testCompile() throws IOException, ClassNotFoundException {
    String[] fileNames = getExampleSources("example");
    String[] args = {"-d", TEST_DIRECTORY, "-encoding", "Shift_JIS"};
    int result = testTarget.run(Strings.append(args, fileNames));
    assertEquals(result, 0);
    File testDirectory = new File(TEST_DIRECTORY);
    File[] files = testDirectory.listFiles(dstFilter);
    ClassLoader loader = new URLClassLoader(new URL[]{testDirectory.toURL()});
    for(int i = 0; i < files.length; i++){
      Class.forName(Paths.cutExtension(files[i].getName()), true, loader);
    }
  }
}
