package onion.compiler.parser

import onion.compiler.pipeline.{CompilerPhase, PhaseContext}
import onion.compiler.source.{InputSourceAdapter, SourceHandle}
import onion.compiler.{AST, CompilerConfig, Parsing}

final class ParsingPhase(config: CompilerConfig) extends CompilerPhase[Seq[SourceHandle], Seq[AST.CompilationUnit]] {
  override val name: String = "Parsing"

  override def run(input: Seq[SourceHandle], ctx: PhaseContext): Seq[AST.CompilationUnit] =
    new Parsing(config).process(input.map(InputSourceAdapter.toInputSource))
}
