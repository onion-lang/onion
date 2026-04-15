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
  private val loadClass: String => ClassType,
  private val reportNode: (SemanticError, AST.Node, Seq[AnyRef]) => Unit,
  val warningReporter: WarningReporter
) {
  def definition: ClassDefinition = currentDefinitionProvider()
  def mapper: NameResolver = currentMapperProvider()
  def staticImportedList: StaticImportList = staticImportsProvider()
  def sourceFile: String = sourceFileProvider()
  def load(name: String): ClassType = loadClass(name)

  def report(error: SemanticError, node: AST.Node, items: AnyRef*): Unit =
    reportNode(error, node, items.toSeq)
}

object TypingBodyContext {
  def fromTyping(typing: Typing): TypingBodyContext =
    new TypingBodyContext(
      table = typing.table_,
      currentDefinitionProvider = () => typing.definition_,
      currentMapperProvider = () => typing.mapper_,
      staticImportsProvider = () => typing.staticImportedList_,
      rootClass = typing.rootClass,
      sourceFileProvider = () => typing.unit_.sourceFile,
      loadClass = typing.load,
      reportNode = (error, node, items) => typing.report(error, node, items*),
      warningReporter = typing.warningReporter_
    )
}
