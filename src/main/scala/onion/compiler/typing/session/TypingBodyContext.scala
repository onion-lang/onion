package onion.compiler.typing.session

import onion.compiler.*
import onion.compiler.TypedAST.{ClassDefinition, ClassType}
import onion.compiler.typing.NameResolver

final class TypingBodyContext(
  val table: ClassTable,
  private val currentDefinitionProvider: () => ClassDefinition,
  private val currentMapperProvider: () => NameResolver,
  private val staticImportsProvider: () => StaticImportList,
  val rootClass: ClassType,
  private val sourceFileProvider: () => String,
  private val loadClassRequired: String => ClassType,
  private val loadClassOption: String => Option[ClassType],
  private val reportNode: (SemanticError, AST.Node, Seq[AnyRef]) => Unit,
  val warningReporter: WarningReporter,
  private val topLevelClassProvider: () => Option[ClassType] = () => None
) {
  def definition: ClassDefinition = currentDefinitionProvider()
  def mapper: NameResolver = currentMapperProvider()
  def staticImportedList: StaticImportList = staticImportsProvider()
  def sourceFile: String = sourceFileProvider()
  /** Loads a class the compiler itself depends on (JDK / onion runtime); throws if absent. */
  def load(name: String): ClassType = loadClassRequired(name)
  /** Loads a class that may legitimately be absent (e.g. user-named imports). */
  def loadOption(name: String): Option[ClassType] = loadClassOption(name)
  /** The synthetic top-level (<File>Main) class hosting top-level functions and val/var, if loaded. */
  def topLevelClass: Option[ClassType] = topLevelClassProvider()

  def report(error: SemanticError, node: AST.Node, items: AnyRef*): Unit =
    reportNode(error, node, items.toSeq)
}

object TypingBodyContext {
  def fromTyping(typing: Typing, unitContext: TypingUnitContext): TypingBodyContext =
    new TypingBodyContext(
      table = typing.table_,
      currentDefinitionProvider = () => unitContext.currentDefinition,
      currentMapperProvider = () => unitContext.currentMapper,
      staticImportsProvider = () => unitContext.staticImports,
      rootClass = typing.rootClass,
      sourceFileProvider = () => unitContext.unit.sourceFile,
      loadClassRequired = typing.loadRequired,
      loadClassOption = typing.load,
      reportNode = (error, node, items) => typing.report(error, node, items*),
      warningReporter = typing.warningReporter_,
      topLevelClassProvider = () => typing.loadTopClass
    )
}
