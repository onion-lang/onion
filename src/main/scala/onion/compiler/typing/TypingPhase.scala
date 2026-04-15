package onion.compiler.typing

import onion.compiler.pipeline.{CompilerPhase, PhaseContext}
import onion.compiler.{AST, CompileWarning, CompilerConfig, TypedAST, Typing}

final case class TypingPhaseResult(
  classes: Seq[TypedAST.ClassDefinition],
  warnings: Seq[CompileWarning],
  typedBindings: Map[AST.Node, TypedAST.Node]
)

final class TypingPhase(config: CompilerConfig) extends CompilerPhase[Seq[AST.CompilationUnit], TypingPhaseResult] {
  override val name: String = "Typing"

  override def run(input: Seq[AST.CompilationUnit], ctx: PhaseContext): TypingPhaseResult = {
    val typing = new Typing(config)
    val classes = typing.process(input)
    TypingPhaseResult(
      classes = classes,
      warnings = typing.warnings,
      typedBindings = typing.typedBindings
    )
  }
}
