package onion.compiler.pipeline

import onion.compiler.{CompiledClass, CompilerConfig, InputSource}
import onion.compiler.source.InputSourceAdapter

final case class PipelineResult(
  classes: Seq[CompiledClass],
  profile: Option[CompileProfile],
  diagnostics: onion.compiler.diagnostics.DiagnosticBag,
  debugArtifacts: DebugArtifacts,
  timings: Seq[PhaseTiming]
)

class CompilerPipeline(config: CompilerConfig) {
  def run(srcs: Seq[InputSource]): PipelineResult = {
    val result = new PipelineRunner(PipelineRunner.defaultPhases(config))
      .run(CompilationRequest(InputSourceAdapter.fromInputSources(srcs), config))
    val profile =
      if (config.verbose || config.compileProfile.enabled) Some(result.toCompileProfile)
      else None
    PipelineResult(result.classes, profile, result.diagnostics, result.debugArtifacts, result.timings)
  }
}
