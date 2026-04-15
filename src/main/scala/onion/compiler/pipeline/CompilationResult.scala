package onion.compiler.pipeline

import onion.compiler.{CompilationOutcome, CompiledClass}
import onion.compiler.diagnostics.DiagnosticBag

final case class CompilationResult(
  classes: Seq[CompiledClass],
  diagnostics: DiagnosticBag,
  debugArtifacts: DebugArtifacts,
  timings: Seq[PhaseTiming],
  sourceCount: Int,
  classpathSize: Int
) {
  def hasErrors: Boolean =
    diagnostics.hasErrors

  def allErrors = diagnostics.allErrors

  def toOutcome: CompilationOutcome =
    if (hasErrors) CompilationOutcome.Failure(allErrors)
    else CompilationOutcome.Success(classes)

  def toCompileProfile: CompileProfile =
    CompileProfile(
      sourceCount = sourceCount,
      classpathSize = classpathSize,
      generatedClasses = classes.size,
      phases = timings.map { timing =>
        PhaseProfile(
          name = timing.name,
          elapsedNanos = timing.elapsedNanos,
          inputCount = timing.inputCount,
          outputCount = timing.outputCount
        )
      }.toVector,
      totalElapsedNanos = timings.map(_.elapsedNanos).sum
    )
}
