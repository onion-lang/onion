package onion.compiler.pipeline

final case class PhaseTiming(
  name: String,
  elapsedNanos: Long,
  inputCount: Int,
  outputCount: Int
) {
  def elapsedMillis: Double = elapsedNanos.toDouble / 1000000.0
}
