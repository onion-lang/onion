/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

import onion.compiler.exceptions.CompilationException
import onion.compiler.pipeline.{CompilationRequest, CompilationResult, PipelineRunner}
import onion.compiler.source.InputSourceAdapter

/**
 * @author Kota Mizushima
 *
 */
class OnionCompiler(val config: CompilerConfig) {
  def compile(fileNames: Array[String]): CompilationOutcome =
    compileDetailed(fileNames).toOutcome

  def compile(srcs: Seq[InputSource]): CompilationOutcome =
    compileDetailed(srcs).toOutcome

  def compileDetailed(fileNames: Array[String]): CompilationResult =
    compileDetailed(fileNames.iterator.map(new FileInputSource(_, sourceCharset)).toSeq)

  // The configured -encoding. Files used to be read in the platform default charset
  // whatever -encoding said.
  private def sourceCharset: java.nio.charset.Charset =
    try java.nio.charset.Charset.forName(config.encoding)
    catch { case _: Exception => java.nio.charset.Charset.defaultCharset() }

  def compileDetailed(srcs: Seq[InputSource]): CompilationResult =
    new PipelineRunner(PipelineRunner.defaultPhases(config))
      .run(CompilationRequest(InputSourceAdapter.fromInputSources(srcs), config))

  def compileOrThrow(fileNames: Array[String]): Seq[CompiledClass] =
    compileDetailed(fileNames) match {
      case result if !result.hasErrors => result.classes
      case result => throw new CompilationException(result.allErrors)
    }

  def compileOrThrow(srcs: Seq[InputSource]): Seq[CompiledClass] =
    compileDetailed(srcs) match {
      case result if !result.hasErrors => result.classes
      case result => throw new CompilationException(result.allErrors)
    }
}
