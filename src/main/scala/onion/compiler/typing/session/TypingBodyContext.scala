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
  private val topLevelClassProvider: () => Option[ClassType] = () => None,
  private val problemCountProvider: () => Int = () => 0,
  private val unitProvider: () => AST.CompilationUnit = () => null
) {
  /**
   * Names assigned by code that can run at an arbitrary point of the script: every top-level
   * `def`, every class/record/enum/extension body, and every lambda anywhere in the unit.
   * A top-level `var` outside this set is only ever written by the script's own straight-line
   * statements, so a null check on it can be flow-narrowed like a local (RFC #2031 option 2).
   */
  lazy val deferredAssignedNames: Set[String] = {
    val unit = unitProvider()
    if (unit == null) Set.empty
    else {
      val fromDeclarations = unit.toplevels.filterNot(_.isInstanceOf[AST.BlockElement])
        .foldLeft(Set.empty[String])((acc, t) => acc ++ AssignedVariableScanner.scan(t))
      fromDeclarations ++ AssignedVariableScanner.assignedInLambdas(unit.toplevels.collect { case b: AST.BlockElement => b })
    }
  }

  /** Typed `Try` nodes created so far in this unit; bracketing a body typing tells whether it made one. */
  var tryCount: Int = 0

  // `AssignedVariableScanner.scan` walks a subtree reflectively. Body typing asked for the
  // same subtrees repeatedly -- a method body once, then every `if`/`while` inside it
  // again for its own branches -- so the answer is kept per node. The AST is immutable.
  private val assignedNamesMemo = new java.util.IdentityHashMap[AST.Node, Set[String]]()
  def assignedNames(node: AST.Node): Set[String] = node match {
    case b: AST.BlockExpression if b.assignedNames != null => b.assignedNames // recorded by the parser
    case _ =>
      val cached = assignedNamesMemo.get(node)
      if (cached != null) cached
      else AssignedVariableScanner.scan(node, assignedNamesMemo)
  }

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

  /** Number of errors reported so far. Callers checkpoint this around a
   *  speculative closure-body typing attempt to tell "the closure body itself
   *  errored" apart from "no overload matched", so a redundant outer
   *  method-not-found can be suppressed (issue #316). */
  def problemCount: Int = problemCountProvider()
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
      topLevelClassProvider = () => typing.loadTopClass,
      problemCountProvider = () => typing.problemCount,
      unitProvider = () => unitContext.unit
    )
}
