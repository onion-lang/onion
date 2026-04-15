package onion.compiler.pipeline

trait CompilerPhase[-In, +Out] {
  def name: String
  def run(input: In, ctx: PhaseContext): Out
}
