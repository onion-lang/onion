package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*
import onion.compiler.typing.session.TypingUnitContext

import scala.collection.mutable.Buffer

private[compiler] final class TopLevelTypingSupport(
  typing: Typing,
  unitContext: TypingUnitContext,
  entryPointSupport: EntryPointSupport,
  translate: (AST.BlockElement, LocalContext) => ActionStatement,
  processClassDeclaration: (AST.ClassDeclaration, LocalContext) => Unit,
  processInterfaceDeclaration: (AST.InterfaceDeclaration, LocalContext) => Unit,
  processRecordDeclaration: (AST.RecordDeclaration, LocalContext) => Unit,
  processEnumDeclaration: (AST.EnumDeclaration, LocalContext) => Unit,
  processExtensionDeclaration: AST.ExtensionDeclaration => Unit,
  processFunctionDeclaration: (AST.FunctionDeclaration, LocalContext) => Unit,
  processGlobalVariableDeclaration: (AST.GlobalVariableDeclaration, LocalContext) => Unit,
  processTopLevelVarDeclaration: (AST.LocalVariableDeclaration, ClassDefinition, LocalContext) => Option[ActionStatement]
) {
  final case class PreparedUnit(
    context: LocalContext,
    statements: Buffer[ActionStatement],
    fieldInitStatements: Buffer[ActionStatement],
    klass: ClassDefinition,
    argsType: Type,
    startMethod: MethodDefinition
  )

  def prepareUnit(unit: AST.CompilationUnit): PreparedUnit = {
    val context = new LocalContext
    // Top-level `val`/`var` declarations are lowered to static fields, which get
    // a default/constructor init, so an uninitialized top-level `val` is allowed
    // here (issue #280 targets uninitialized *local* vals only).
    context.allowUninitializedLocal = true
    val statements = Buffer[ActionStatement]()
    val fieldInitStatements = Buffer[ActionStatement]()
    typing.find(typing.topClass).foreach(unitContext.currentMapper = _)
    val klass = typing.loadTopClass.collect { case cd: ClassDefinition => cd }.orNull
    val argsType = entryPointSupport.stringArgsType
    val startMethod = entryPointSupport.createStartMethod(unit, klass, argsType)
    // A closure that captures a top-level `var`/`val` must share the same cell
    // as the enclosing scope (like inside a function), otherwise it captures a
    // disconnected copy and outer mutations are lost. Mark such variables as
    // boxed before their bindings are added, mirroring markCapturedVariables in
    // MethodBodySupport. `args` is a real parameter slot, so it is excluded.
    val blockElements = unit.toplevels.collect { case be: AST.BlockElement => be }
    context.markAsBoxed(CapturedVariableScanner.scanElements(blockElements, Set("args")))
    // A top-level `var` never reassigned across the script body is effectively
    // final and can be smart-cast like a `val` (issue #273).
    context.setReassignedNames(blockElements.flatMap(AssignedVariableScanner.scan).toSet)
    context.add("args", argsType)
    PreparedUnit(context, statements, fieldInitStatements, klass, argsType, startMethod)
  }

  def processToplevels(toplevels: Seq[AST.Toplevel], prepared: PreparedUnit): Unit = {
    for (element <- toplevels) {
      if (!element.isInstanceOf[AST.TypeDeclaration]) unitContext.currentDefinition = prepared.klass
      element match {
        case node: AST.LocalVariableDeclaration if prepared.klass != null =>
          prepared.context.setMethod(prepared.startMethod)
          processTopLevelVarDeclaration(node, prepared.klass, prepared.context) match {
            case Some(stmt) =>
              // A top-level `val`/`var` field initializer. It goes into `start`
              // (as before) but is also recorded so it can be run before the
              // user's `def main` when one exists (otherwise `start` is never
              // called and the field keeps its default — see #270).
              prepared.statements += stmt
              prepared.fieldInitStatements += stmt
            case None => prepared.statements += translate(node, prepared.context)
          }
        case node: AST.BlockElement =>
          prepared.context.setMethod(prepared.startMethod)
          prepared.statements += translate(node, prepared.context)
        case node: AST.ClassDeclaration =>
          processClassDeclaration(node, prepared.context)
        case node: AST.InterfaceDeclaration =>
          processInterfaceDeclaration(node, prepared.context)
        case node: AST.RecordDeclaration =>
          processRecordDeclaration(node, prepared.context)
        case node: AST.EnumDeclaration =>
          processEnumDeclaration(node, prepared.context)
        case node: AST.ExtensionDeclaration =>
          processExtensionDeclaration(node)
        case node: AST.FunctionDeclaration =>
          processFunctionDeclaration(node, prepared.context)
        case node: AST.GlobalVariableDeclaration =>
          processGlobalVariableDeclaration(node, prepared.context)
        case _ =>
      }
    }
  }

  def finishUnit(prepared: PreparedUnit): Unit = {
    if (prepared.klass != null) {
      entryPointSupport.attachStartAndMain(
        prepared.klass,
        prepared.startMethod,
        prepared.statements,
        prepared.fieldInitStatements,
        prepared.context,
        prepared.argsType
      )
    }
  }
}
