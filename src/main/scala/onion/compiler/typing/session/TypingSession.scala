package onion.compiler.typing.session

import onion.compiler.{AST, CompilerConfig}
import onion.compiler.typing.TypeParamScope

import scala.collection.mutable.HashMap

final class TypingSession(
  val config: CompilerConfig,
  val global: TypingGlobalState,
  emptyTypeParams: TypeParamScope
) {
  private val unitContexts = HashMap[AST.CompilationUnit, TypingUnitContext]()
  private var activeContext: TypingUnitContext = null

  def activate(unit: AST.CompilationUnit): TypingUnitContext = {
    val context = contextFor(unit)
    context.currentDefinition = null
    context.currentMapper = null
    context.currentAccess = 0
    context.currentTypeParams = emptyTypeParams
    context.reportingSuppressed = 0
    activeContext = context
    context
  }

  def currentContext: TypingUnitContext =
    activeContext

  def contextFor(unit: AST.CompilationUnit): TypingUnitContext =
    unitContexts.getOrElseUpdate(unit, TypingUnitContext.initial(unit, emptyTypeParams))
}
