package onion.compiler.pipeline

import onion.compiler._
import onion.compiler.codegen.TypedAstCodeGeneration

import java.lang.System.{nanoTime => now}

final case class PipelineResult(
  classes: Seq[CompiledClass],
  profile: Option[CompileProfile]
)

class CompilerPipeline(config: CompilerConfig) {
  private val profilingEnabled = config.verbose || config.compileProfile.enabled

  def run(srcs: Seq[InputSource]): PipelineResult = {
    val parsing = new Parsing(config)
    val rewriting = new Rewriting(config)
    val typing = new Typing(config)
    val tailCallOpt = new optimization.TailCallOptimization(config)
    val mutualRecOpt = new optimization.MutualRecursionOptimization(config)
    val generating = new TypedAstCodeGeneration(config)

    val phases = Vector.newBuilder[PhaseProfile]
    val totalStart = if (profilingEnabled) now() else 0L

    def measure[A](name: String, inputCount: Int)(block: => A): A = {
      if (!profilingEnabled) return block
      val start = now()
      val result = block
      phases += PhaseProfile(name, now() - start, inputCount, countOf(result))
      result
    }

    val parsed = measure("Parsing", srcs.size)(parsing.process(srcs))
    if (config.dumpAst) DiagnosticsPrinter.dumpAst(parsed)
    val rewritten = measure("Rewriting", parsed.size)(rewriting.process(parsed))
    val typed = measure("Typing", rewritten.size)(typing.process(rewritten))
    if (config.dumpTypedAst) DiagnosticsPrinter.dumpTyped(typed)
    val optimized1 = measure("TailCallOpt", typed.size)(tailCallOpt.process(typed))
    val optimized2 = measure("MutualRecOpt", optimized1.size)(mutualRecOpt.process(optimized1))
    val generated = measure("CodeGen", optimized2.size)(generating.process(optimized2))

    val profile =
      if (!profilingEnabled) None
      else
        Some(
          CompileProfile(
            sourceCount = srcs.size,
            classpathSize = config.classPath.size,
            generatedClasses = generated.size,
            phases = phases.result(),
            totalElapsedNanos = now() - totalStart
          )
        )

    PipelineResult(generated, profile)
  }

  private def countOf(value: Any): Int =
    value match
      case seq: Seq[?] => seq.size
      case array: Array[?] => array.length
      case null => 0
      case _ => 1
}
