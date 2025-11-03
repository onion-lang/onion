/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

/**
 * Models the outcome of a compilation request.
 */
sealed trait CompilationOutcome

object CompilationOutcome {
  final case class Success(classes: Seq[CompiledClass]) extends CompilationOutcome
  final case class Failure(errors: Seq[CompileError]) extends CompilationOutcome
}
