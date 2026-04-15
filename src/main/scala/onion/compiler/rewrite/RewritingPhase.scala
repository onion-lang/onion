package onion.compiler.rewrite

import onion.compiler.pipeline.{CompilerPhase, PhaseContext}
import onion.compiler.{AST, CompilerConfig, Rewriting}

final class RewritingPhase(config: CompilerConfig) extends CompilerPhase[Seq[AST.CompilationUnit], Seq[AST.CompilationUnit]] {
  override val name: String = "Rewriting"

  override def run(input: Seq[AST.CompilationUnit], ctx: PhaseContext): Seq[AST.CompilationUnit] =
    new Rewriting(config).process(input)
}
