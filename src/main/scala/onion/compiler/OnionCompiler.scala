/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

import onion.compiler.CompilationOutcome.{Failure, Success}
import onion.compiler.exceptions.CompilationException

/**
 * @author Kota Mizushima
 *
 */
class OnionCompiler(val config: CompilerConfig) {

  def compile(fileNames: Array[String]): CompilationOutcome = {
    val sources = fileNames.iterator.map(new FileInputSource(_)).toSeq
    compile(sources)
  }

  def compile(srcs: Seq[InputSource]): CompilationOutcome = {
    val pipeline =
      new Parsing(config)
        .andThen(new Rewriting(config))
        .andThen(new Typing(config))
        .andThen(new TypedGenerating(config))

    try {
      Success(pipeline.process(srcs))
    } catch {
      case e: CompilationException =>
        Failure(e.problems.toIndexedSeq)
    }
  }

  def compileOrThrow(fileNames: Array[String]): Seq[CompiledClass] =
    compile(fileNames) match {
      case Success(classes) => classes
      case Failure(errors) => throw new CompilationException(errors)
    }

  def compileOrThrow(srcs: Seq[InputSource]): Seq[CompiledClass] =
    compile(srcs) match {
      case Success(classes) => classes
      case Failure(errors) => throw new CompilationException(errors)
    }
}
