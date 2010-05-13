/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler;

import onion.compiler.util.ArrayUtil;

/**
 * @author Kota Mizushima
 * Date: 2005/04/08
 */
public class CompilerConfig implements Cloneable {
  private final String[] classPath;
  private final String superClass;
  private final String encoding;
  private final String outputDirectory;
  private final int maxErrorReports;

  public CompilerConfig(
    String[] classPath, String superClass, String encoding, 
    String outputDirectory, int maxErrorReports
  ) {
    this.classPath = (String[]) classPath.clone();
    this.superClass = superClass;
    this.encoding = encoding;
    this.outputDirectory = outputDirectory;
    this.maxErrorReports = maxErrorReports;
  }
  
  public Object clone(){
    return new CompilerConfig(
      (String[])classPath.clone(),
      superClass,
      encoding,
      outputDirectory,
      maxErrorReports
    );
  }
  
  public String[] getClassPath() {
    return (String[])classPath.clone();
  }
  
  public String getSuperClass() {
    return superClass;
  }
  
  public String getEncoding() {
    return encoding;
  }
  
  public String getOutputDirectory() {
    return outputDirectory;
  }
  
  public int getMaxErrorReports() {
    return maxErrorReports;
  }
  
  public boolean equals(Object object) {
    CompilerConfig another = (CompilerConfig) object;
    return 
    			encoding.equals(another.encoding)
      &&  superClass.equals(another.superClass)
    	&&	ArrayUtil.equals(classPath, another.classPath)
    	&&	outputDirectory.equals(another.outputDirectory)
    	&&	maxErrorReports == another.maxErrorReports;
  }
}
