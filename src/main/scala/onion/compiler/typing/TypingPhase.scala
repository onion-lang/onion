package onion.compiler.typing

import onion.compiler.pipeline.{CompilerPhase, PhaseContext}
import onion.compiler.{AST, CompileWarning, CompilerConfig, TypedAST, Typing, WarningCategory}
import onion.compiler.exceptions.CompilationException

final case class TypingPhaseResult(
  classes: Seq[TypedAST.ClassDefinition],
  warnings: Seq[CompileWarning],
  typedBindings: Map[AST.Node, TypedAST.Node]
)

final class TypingPhase(config: CompilerConfig) extends CompilerPhase[Seq[AST.CompilationUnit], TypingPhaseResult] {
  override val name: String = "Typing"

  override def run(input: Seq[AST.CompilationUnit], ctx: PhaseContext): TypingPhaseResult = {
    val typing = new Typing(config)
    val classes =
      try typing.process(input)
      catch {
        case e: CompilationException =>
          // A failed typing run drops its warnings, but a library effect-table warning
          // (W0017/W0018) is often the reason for the failure: the E0077 `unknown` a
          // tool gets when the library's table was ignored. Keep exactly those.
          ctx.addWarnings(typing.warnings.filter(w => TypingPhase.KeptOnFailure(w.category)))
          throw e
      }
    TypingPhaseResult(
      classes = classes,
      warnings = typing.warnings,
      typedBindings = typing.typedBindings
    )
  }
}

object TypingPhase {
  private val KeptOnFailure: Set[WarningCategory] =
    Set(WarningCategory.LibraryEffectTableMalformed, WarningCategory.LibraryEffectTableForeignClass)
}
