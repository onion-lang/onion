package onion.compiler.pipeline

enum CompileProfileFormat:
  case Text
  case Json

final case class CompileProfileSettings(
  enabled: Boolean = false,
  format: CompileProfileFormat = CompileProfileFormat.Text,
  output: Option[String] = None
)

final case class PhaseProfile(
  name: String,
  elapsedNanos: Long,
  inputCount: Int,
  outputCount: Int
):
  def elapsedMillis: Double = elapsedNanos.toDouble / 1000000.0

final case class CompileProfile(
  sourceCount: Int,
  classpathSize: Int,
  generatedClasses: Int,
  phases: Vector[PhaseProfile],
  totalElapsedNanos: Long
):
  def totalElapsedMillis: Double = totalElapsedNanos.toDouble / 1000000.0
