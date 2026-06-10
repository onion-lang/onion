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
  processEnumDeclaration: (AST.EnumDeclaration, LocalContext) => Unit,
  processExtensionDeclaration: AST.ExtensionDeclaration => Unit,
  processFunctionDeclaration: (AST.FunctionDeclaration, LocalContext) => Unit,
  processGlobalVariableDeclaration: (AST.GlobalVariableDeclaration, LocalContext) => Unit
) {
  final case class PreparedUnit(
    context: LocalContext,
    statements: Buffer[ActionStatement],
    klass: ClassDefinition,
    argsType: Type,
    startMethod: MethodDefinition
  )

  def prepareUnit(unit: AST.CompilationUnit): PreparedUnit = {
    val context = new LocalContext
    val statements = Buffer[ActionStatement]()
    typing.find(typing.topClass).foreach(unitContext.currentMapper = _)
    val klass = typing.loadTopClass.asInstanceOf[ClassDefinition]
    val argsType = entryPointSupport.stringArgsType
    val startMethod = entryPointSupport.createStartMethod(unit, klass, argsType)
    context.add("args", argsType)
    PreparedUnit(context, statements, klass, argsType, startMethod)
  }

  def processToplevels(toplevels: Seq[AST.Toplevel], prepared: PreparedUnit): Unit = {
    for (element <- toplevels) {
      if (!element.isInstanceOf[AST.TypeDeclaration]) unitContext.currentDefinition = prepared.klass
      element match {
        case node: AST.BlockElement =>
          prepared.context.setMethod(prepared.startMethod)
          prepared.statements += translate(node, prepared.context)
        case node: AST.ClassDeclaration =>
          processClassDeclaration(node, prepared.context)
        case node: AST.InterfaceDeclaration =>
          processInterfaceDeclaration(node, prepared.context)
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
        prepared.context,
        prepared.argsType
      )
    }
  }
}
