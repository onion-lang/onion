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
import onion.compiler.pipeline.{CompileProfileReporter, CompilerPipeline}
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
    try {
      val result = new CompilerPipeline(config).run(srcs)
      result.profile.foreach { profile =>
        if (config.verbose) {
          System.err.println(CompileProfileReporter.renderVerbose(profile))
        }
        if (config.compileProfile.enabled) {
          CompileProfileReporter.report(profile, config.compileProfile)
        }
      }
      Success(result.classes)
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
