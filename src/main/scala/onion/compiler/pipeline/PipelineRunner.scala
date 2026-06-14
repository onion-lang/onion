package onion.compiler.pipeline

import onion.compiler.*
import onion.compiler.backend.BytecodeGenerationPhase
import onion.compiler.diagnostics.DiagnosticBag
import onion.compiler.exceptions.CompilationException
import onion.compiler.parser.ParsingPhase
import onion.compiler.rewrite.RewritingPhase
import onion.compiler.source.SourceHandle
import onion.compiler.typing.{TypingPhase, TypingPhaseResult}

import java.lang.System.{nanoTime => now}
import scala.util.control.NonFatal

final case class CompilationPhases(
  parsing: CompilerPhase[Seq[SourceHandle], Seq[AST.CompilationUnit]],
  rewriting: CompilerPhase[Seq[AST.CompilationUnit], Seq[AST.CompilationUnit]],
  typing: CompilerPhase[Seq[AST.CompilationUnit], TypingPhaseResult],
  tailCallOptimization: CompilerPhase[Seq[TypedAST.ClassDefinition], Seq[TypedAST.ClassDefinition]],
  mutualRecursionOptimization: CompilerPhase[Seq[TypedAST.ClassDefinition], Seq[TypedAST.ClassDefinition]],
  bytecodeGeneration: CompilerPhase[Seq[TypedAST.ClassDefinition], Seq[CompiledClass]],
  lawCheck: CompilerPhase[Seq[CompiledClass], Seq[CompiledClass]]
)

object PipelineRunner {
  def defaultPhases(config: CompilerConfig): CompilationPhases =
    CompilationPhases(
      parsing = new ParsingPhase(config),
      rewriting = new RewritingPhase(config),
      typing = new TypingPhase(config),
      tailCallOptimization = new optimization.TailCallOptimization(config) with CompilerPhase[Seq[TypedAST.ClassDefinition], Seq[TypedAST.ClassDefinition]] {
        override def name: String = "TailCallOptimization"
        override def run(input: Seq[TypedAST.ClassDefinition], ctx: PhaseContext): Seq[TypedAST.ClassDefinition] = process(input)
      },
      mutualRecursionOptimization = new optimization.MutualRecursionOptimization(config) with CompilerPhase[Seq[TypedAST.ClassDefinition], Seq[TypedAST.ClassDefinition]] {
        override def name: String = "MutualRecursionOptimization"
        override def run(input: Seq[TypedAST.ClassDefinition], ctx: PhaseContext): Seq[TypedAST.ClassDefinition] = process(input)
      },
      bytecodeGeneration = new BytecodeGenerationPhase(config),
      lawCheck = new onion.compiler.verification.LawCheckPhase(config)
    )
}

final class PipelineRunner(phases: CompilationPhases) {
  private val internalErrorCode = "I0000"

  def run(request: CompilationRequest): CompilationResult = {
    val ctx = new PhaseContext(request.config)

    runPhase(phases.parsing, request.sources, ctx) { units =>
      ctx.setParsedUnits(units)
    } match {
      case None => result(Seq.empty, ctx, request)
      case Some(parsed) =>
        runAfterParsing(parsed, ctx, request)
    }
  }

  private def runAfterParsing(
    parsed: Seq[AST.CompilationUnit],
    ctx: PhaseContext,
    request: CompilationRequest
  ): CompilationResult =
    runPhase(phases.rewriting, parsed, ctx) { units =>
      ctx.setRewrittenUnits(units)
    } match {
      case None => result(Seq.empty, ctx, request)
      case Some(rewritten) =>
        runAfterRewriting(rewritten, ctx, request)
    }

  private def runAfterRewriting(
    rewritten: Seq[AST.CompilationUnit],
    ctx: PhaseContext,
    request: CompilationRequest
  ): CompilationResult =
    runPhase(phases.typing, rewritten, ctx) { typedResult =>
      ctx.setTypedClasses(typedResult.classes)
      ctx.setTypedBindings(typedResult.typedBindings)
      ctx.addWarnings(typedResult.warnings)
    } match {
      case None => result(Seq.empty, ctx, request)
      case Some(typed) =>
        if (request.config.warningLevel == WarningLevel.Error && ctx.diagnostics.warnings.nonEmpty) {
          ctx.addErrors(promoteWarnings(ctx.diagnostics.warnings))
          result(Seq.empty, ctx, request)
        } else {
          runAfterTyping(typed.classes, ctx, request)
        }
    }

  private def runAfterTyping(
    typed: Seq[TypedAST.ClassDefinition],
    ctx: PhaseContext,
    request: CompilationRequest
  ): CompilationResult =
    runPhase(phases.tailCallOptimization, typed, ctx)(_ => ()) match {
      case None => result(Seq.empty, ctx, request)
      case Some(optimizedTail) =>
        runAfterTailCallOptimization(optimizedTail, ctx, request)
    }

  private def runAfterTailCallOptimization(
    optimizedTail: Seq[TypedAST.ClassDefinition],
    ctx: PhaseContext,
    request: CompilationRequest
  ): CompilationResult =
    runPhase(phases.mutualRecursionOptimization, optimizedTail, ctx)(_ => ()) match {
      case None => result(Seq.empty, ctx, request)
      case Some(optimizedMutual) =>
        runPhase(phases.bytecodeGeneration, optimizedMutual, ctx)(_ => ()) match {
          case None => result(Seq.empty, ctx, request)
          case Some(classes) =>
            // B3: run law/example checks on the generated classes; a failure surfaces as a
            // CompilationException (caught by runPhase) and fails compilation.
            runPhase(phases.lawCheck, classes, ctx)(_ => ()) match {
              case None => result(Seq.empty, ctx, request)
              case Some(checked) => result(checked, ctx, request)
            }
        }
    }

  private def runPhase[In, Out](
    phase: CompilerPhase[In, Out],
    input: In,
    ctx: PhaseContext
  )(capture: Out => Unit): Option[Out] = {
    val start = now()
    try {
      val output = phase.run(input, ctx)
      ctx.timings += PhaseTiming(phase.name, now() - start, countOf(input), countOf(output))
      capture(output)
      Some(output)
    } catch {
      case e: CompilationException =>
        ctx.timings += PhaseTiming(phase.name, now() - start, countOf(input), 0)
        ctx.addErrors(e.problems)
        None
      case NonFatal(e) =>
        ctx.timings += PhaseTiming(phase.name, now() - start, countOf(input), 0)
        ctx.addInternals(Seq(internalError(phase.name, e)))
        None
    }
  }

  private def result(classes: Seq[CompiledClass], ctx: PhaseContext, request: CompilationRequest): CompilationResult =
    CompilationResult(
      classes = classes,
      diagnostics = ctx.diagnostics,
      debugArtifacts = ctx.debugArtifacts,
      timings = ctx.timings.toSeq,
      sourceCount = request.sources.size,
      classpathSize = request.config.classPath.size
    )

  private def internalError(phaseName: String, throwable: Throwable): CompileError = {
    val message = Option(throwable.getMessage).filter(_.nonEmpty).getOrElse(throwable.getClass.getSimpleName)
    val trace = throwable.getStackTrace.iterator
      .filter(e => e.getClassName.startsWith("onion."))
      .take(5)
      .map(e => s"    at ${e.getClassName}.${e.getMethodName}(${e.getFileName}:${e.getLineNumber})")
      .mkString("\n")
    val detail = if (trace.isEmpty) message else s"$message\n$trace"
    CompileError(null, null, s"Internal compiler error in $phaseName: $detail", Some(internalErrorCode))
  }

  private def promoteWarnings(warnings: Seq[CompileWarning]): Seq[CompileError] =
    warnings.map { warning =>
      CompileError(warning.sourceFile, warning.location, warning.message, warning.code)
    }

  private def countOf(value: Any): Int =
    value match {
      case seq: Seq[?] => seq.size
      case array: Array[?] => array.length
      case null => 0
      case _ => 1
    }
}
