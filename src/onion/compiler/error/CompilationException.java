/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.error;

import java.util.List;

/**
 * @author Kota Mizushima
 */
public class CompilationException extends RuntimeException {    
  private List problems;

  public CompilationException(List problems) {
    this.problems = problems;
  }
  
  public CompileError get(int index) {
    return (CompileError)problems.get(index);
  }
  
  public int size() {
    return problems.size();
  }
}
