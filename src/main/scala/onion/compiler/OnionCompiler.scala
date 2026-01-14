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
import scala.util.control.NonFatal

/**
 * @author Kota Mizushima
 *
 */
class OnionCompiler(val config: CompilerConfig) {
  private val InternalErrorCode = "I0000"

  private def internalError(e: Throwable): CompileError = {
    val message = Option(e.getMessage).filter(_.nonEmpty).getOrElse(e.getClass.getSimpleName)
    CompileError(null, null, s"Internal compiler error: $message", Some(InternalErrorCode))
  }

  def compile(fileNames: Array[String]): CompilationOutcome = {
    val sources = fileNames.iterator.map(new FileInputSource(_)).toSeq
    compile(sources)
  }

  def compile(srcs: Seq[InputSource]): CompilationOutcome = {
    if (config.verbose) {
      compileVerbose(srcs)
    } else {
      compileNormal(srcs)
    }
  }

  private def compileNormal(srcs: Seq[InputSource]): CompilationOutcome = {
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
      case NonFatal(e) =>
        Failure(Seq(internalError(e)))
    }
  }

  private def compileVerbose(srcs: Seq[InputSource]): CompilationOutcome = {
    import java.lang.System.{currentTimeMillis => now}

    def timed[A](phaseName: String)(block: => A): A = {
      val start = now()
      val result = block
      val elapsed = now() - start
      System.err.println(f"[verbose] $phaseName: ${elapsed}ms")
      result
    }

    try {
      val totalStart = now()

      val parsing = new Parsing(config)
      val rewriting = new Rewriting(config)
      val typing = new Typing(config)
      val generating = new TypedGenerating(config)

      val parsed = timed("Parsing")(parsing.process(srcs))
      val rewritten = timed("Rewriting")(rewriting.process(parsed))
      val typed = timed("Typing")(typing.process(rewritten))
      val generated = timed("CodeGen")(generating.process(typed))

      val totalElapsed = now() - totalStart
      System.err.println(f"[verbose] Total: ${totalElapsed}ms (${srcs.size} source files)")

      Success(generated)
    } catch {
      case e: CompilationException =>
        Failure(e.problems.toIndexedSeq)
      case NonFatal(e) =>
        Failure(Seq(internalError(e)))
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
