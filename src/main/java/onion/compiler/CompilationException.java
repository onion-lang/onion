/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author Kota Mizushima
 */
public class CompilationException extends RuntimeException implements Iterable<CompileError> {
  private List<CompileError> problems;

  public CompilationException(List<CompileError> problems) {
    this.problems = problems;
  }

  public List<CompileError> problems() {
    return Collections.unmodifiableList(problems);
  }

  public int size() {
    return problems.size();
  }

  public Iterator<CompileError> iterator() {
    return problems.iterator();
  }
}

