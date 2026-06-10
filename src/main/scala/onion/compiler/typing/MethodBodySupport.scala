package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.typing.session.{TypingBodyContext, TypingUnitContext}

private[compiler] final class MethodBodySupport(
  typing: Typing,
  unitContext: TypingUnitContext,
  bodyContext: TypingBodyContext,
  typed: (AST.Expression, LocalContext, Type) => Option[Term],
  typedTerms: (Array[AST.Expression], LocalContext) => Array[Term],
  translate: (AST.BlockElement, LocalContext) => ActionStatement,
  addReturnNode: (ActionStatement, Type) => StatementBlock
) {
  def processMethodLikeBody(
    method: MethodDefinition,
    args: List[AST.Argument],
    block: AST.BlockExpression
  ): Unit = {
    val context = prepareMethodContext(method, args, block)
    val translatedBlock = addReturnNode(translate(block, context).asInstanceOf[StatementBlock], method.returnType)
    method.setBlock(translatedBlock)
    method.setFrame(context.getContextFrame)
    reportUnused(context)
  }

  def prepareConstructorContext(constructor: ConstructorDefinition, args: List[AST.Argument]): LocalContext = {
    val context = new LocalContext
    context.setConstructor(constructor)
    val paramTypes = constructor.getArgs
    bindParameters(context, args, paramTypes)
    constructor.setArgumentsWithDefaults(buildArgumentsWithDefaults(args, paramTypes, context))
    context
  }

  def processConstructorDeclaration(node: AST.ConstructorDeclaration): Unit =
    typing.kernelNodeOf[ConstructorDefinition](node).foreach { constructor =>
      val context = prepareConstructorContext(constructor, node.args)
      val params = typedTerms(node.superInits.toArray, context)
      val superClass = bodyContext.definition.superClass
      val matched = superClass.findConstructor(params)
      if matched.length == 0 then
        bodyContext.report(CONSTRUCTOR_NOT_FOUND, node, superClass, termTypes(params), superClass.constructors)
      else if matched.length > 1 then
        bodyContext.report(AMBIGUOUS_CONSTRUCTOR, node, Array[AnyRef](superClass, termTypes(params)), Array[AnyRef](superClass, termTypes(params)))
      else
        val init = new Super(superClass, matched(0).getArgs, params)
        finishConstructorBody(constructor, init, node.block, context)
      reportConstructorOnly(context)
    }

  def finishConstructorBody(
    constructor: ConstructorDefinition,
    initializer: Super,
    block: AST.BlockExpression,
    context: LocalContext
  ): Unit = {
    constructor.superInitializer = initializer
    constructor.block = addReturnNode(translate(block, context).asInstanceOf[StatementBlock], BasicType.VOID)
    constructor.frame = context.getContextFrame
  }

  def reportConstructorOnly(context: LocalContext): Unit =
    reportUnused(context)

  def processExtensionMethodBody(
    extMethod: ExtensionMethodDefinition,
    staticMethod: MethodDefinition,
    node: AST.MethodDeclaration,
    receiverType: Type
  ): Unit = {
    val context = prepareExtensionContext(staticMethod, node, receiverType)
    val translatedBlock = addReturnNode(translate(node.block, context).asInstanceOf[StatementBlock], staticMethod.returnType)
    staticMethod.setBlock(translatedBlock)
    staticMethod.setFrame(context.getContextFrame)
    extMethod.setBlock(translatedBlock)
    extMethod.setFrame(context.getContextFrame)
    reportUnused(context)
  }

  def processExtensionMethodDeclaration(
    node: AST.MethodDeclaration,
    receiverType: Type,
    definition: ClassDefinition
  ): Unit =
    if node.block != null then
      typing.kernelNodeOf[ExtensionMethodDefinition](node).foreach { extMethod =>
        val methodTypeParams = typing.declaredTypeParams_.getOrElse(node, Seq())
        typing.openTypeParams(unitContext.currentTypeParams ++ methodTypeParams) {
          val staticMethod = findStaticExtensionMethod(definition, node, extMethod, receiverType)
          if staticMethod != null then
            processExtensionMethodBody(extMethod, staticMethod, node, receiverType)
        }
      }

  private def prepareMethodContext(
    method: MethodDefinition,
    args: List[AST.Argument],
    block: AST.BlockExpression
  ): LocalContext = {
    val context = new LocalContext
    if (method.modifier & AST.M_STATIC) != 0 then
      context.setStatic(true)
    context.setMethod(method)
    val arguments = method.arguments
    markCapturedVariables(context, args, block)
    bindParameters(context, args, arguments)
    method.setArgumentsWithDefaults(buildArgumentsWithDefaults(args, arguments, context))
    context
  }

  private def prepareExtensionContext(
    staticMethod: MethodDefinition,
    node: AST.MethodDeclaration,
    receiverType: Type
  ): LocalContext = {
    val context = new LocalContext
    context.setStatic(true)
    context.setMethod(staticMethod)

    val extArgs = receiverArgument(node, receiverType) :: node.args
    markCapturedVariables(context, extArgs, node.block)
    context.add("this", receiverType)

    val arguments = staticMethod.arguments
    for ((arg, i) <- node.args.zipWithIndex) {
      context.add(arg.name, arguments(i + 1))
    }

    staticMethod.setArgumentsWithDefaults(buildArgumentsWithDefaults(extArgs, arguments, context))
    context
  }

  private def findStaticExtensionMethod(
    definition: ClassDefinition,
    node: AST.MethodDeclaration,
    extMethod: ExtensionMethodDefinition,
    receiverType: Type
  ): MethodDefinition =
    definition
      .methods(node.name)
      .find { method =>
        (method.modifier & AST.M_STATIC) != 0 &&
        method.arguments.length == extMethod.arguments.length + 1 &&
        method.arguments(0) == receiverType
      }
      .map(_.asInstanceOf[MethodDefinition])
      .orNull

  private def receiverArgument(node: AST.MethodDeclaration, receiverType: Type): AST.Argument =
    AST.Argument(
      node.location,
      "this",
      AST.TypeNode(node.location, AST.ReferenceType(receiverType.name, true), false)
    )

  private def bindParameters(context: LocalContext, args: List[AST.Argument], types: Array[Type]): Unit = {
    var i = 0
    args.foreach { arg =>
      context.add(arg.name, types(i))
      context.recordDeclaration(arg.name, arg.location, isParameter = true)
      i += 1
    }
  }

  private def markCapturedVariables(context: LocalContext, args: List[AST.Argument], block: AST.BlockExpression): Unit = {
    val paramNames = args.map(_.name).toSet
    val capturedVars = CapturedVariableScanner.scan(block, paramNames)
    context.markAsBoxed(capturedVars)
  }

  private def buildArgumentsWithDefaults(
    args: List[AST.Argument],
    types: Array[Type],
    context: LocalContext
  ): Array[TypedAST.MethodArgument] =
    args.zipWithIndex.map { case (arg, i) =>
      val defaultTerm = Option(arg.defaultValue).flatMap { expr =>
        typed(expr, context, types(i))
      }
      TypedAST.MethodArgument(arg.name, types(i), defaultTerm)
    }.toArray

  private def reportUnused(context: LocalContext): Unit =
    typing.reportUnusedVariables(context)

  private def termTypes(terms: Array[Term]): Array[Type] =
    if terms == null then Array.empty else terms.map(_.`type`)
}
