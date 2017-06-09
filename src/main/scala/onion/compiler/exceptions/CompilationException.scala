/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.exceptions

import onion.compiler.CompileError

/**
 * @author Kota Mizushima
 * Exception thrown when compilation errors occured.
 */
class CompilationException(val problems : Seq[CompileError]) extends RuntimeException with Iterable[CompileError] {

  override def size: Int = problems.size

  override def iterator(): Iterator[CompileError] = problems.iterator
}

