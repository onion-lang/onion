package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.TypedAST.BinaryTerm.Kind as BinaryKind
import onion.compiler.TypedAST.BinaryTerm.Kind.*
import onion.compiler.TypedAST.UnaryTerm.Kind as UnaryKind
import onion.compiler.TypedAST.UnaryTerm.Kind.*
import onion.compiler.typing.session.{TypingBodyContext, TypingUnitContext}

import scala.jdk.CollectionConverters.*
import scala.collection.mutable.{Buffer, HashMap, Map, Set => MutableSet, Stack}

import TypeNarrowingAnalysis.NarrowingInfo

final class TypingBodyPass(private val typing: Typing, private val unitContext: TypingUnitContext) {
  private val unit = unitContext.unit
  private val bodyContext = TypingBodyContext.fromTyping(typing, unitContext)
  private val methodCallTyping = new MethodCallTyping(typing, bodyContext, this)
  private val assignmentTyping = new AssignmentTyping(typing, bodyContext, this)
  private[typing] val operatorTyping = new OperatorTyping(typing, bodyContext, this)
  private val expressionFormTyping = new ExpressionFormTyping(typing, bodyContext, this)
  private val closureTyping = new ClosureTyping(typing, bodyContext, this)
  private val blockElementLowering = new BlockElementLowering(typing, bodyContext, this)
  private[typing] val controlExpressionTyping = new ControlExpressionTyping(typing, bodyContext, this)
  private val additionTyping = new AdditionTyping(typing, bodyContext, this)
  private val methodLookupSupport = new MethodLookupSupport(typing, bodyContext)
  private val classInitializerSupport = new ClassInitializerSupport(typing, typed(_, _, _), processAssignable)
  private val methodBodySupport = new MethodBodySupport(typing, unitContext, bodyContext, typed(_, _, _), typedTerms, translate, addReturnNode)
  private val entryPointSupport = new EntryPointSupport(typing, addReturnNode)
  private val assignabilitySupport = new AssignabilitySupport(typing, bodyContext)
  private val expressionDispatchSupport = new ExpressionDispatchSupport(this)
  private val patternMatchSupport = new PatternMatchSupport(bodyContext, (node, context) => typed(node, context), createEqualsForRef)
  private val simpleExpressionTypingSupport = new SimpleExpressionTypingSupport(
    bodyContext,
    typed(_, _, _),
    typeMemberSelection(_, _),
    typeAssignment(_, _)
  )
  private val declarationBodySupport = new DeclarationBodySupport(
    typing,
    unitContext,
    classInitializerSupport,
    methodBodySupport,
    processMethodDeclaration,
    processConstructorDeclaration
  )
  private val topLevelTypingSupport = new TopLevelTypingSupport(
    typing,
    unitContext,
    entryPointSupport,
    translate,
    processClassDeclaration,
    processInterfaceDeclaration,
    processRecordDeclaration,
    processEnumDeclaration,
    processExtensionDeclaration,
    processFunctionDeclaration,
    processGlobalVariableDeclaration,
    processTopLevelVarDeclaration
  )
  def run(): Unit = runUnit()

  def createEqualsForRef(lhs: Term, rhs: Term): Term =
    operatorTyping.createEquals(EQUAL, lhs, rhs)

  /** Extract smart cast narrowing info from a condition expression. */
  def extractNarrowing(condition: AST.Expression, context: LocalContext): NarrowingInfo =
    controlExpressionTyping.extractNarrowing(condition, context)

  def processNodes(nodes: Array[AST.Expression], typeRef: Type, bind: ClosureLocalBinding, context: LocalContext): Term =
    patternMatchSupport.processNodes(nodes, typeRef, bind, context)

  def processAssignable(node: AST.Node, expected: Type, actual: Term): Term =
    assignabilitySupport.processAssignable(node, expected, actual)
  def openClosure[A](context: LocalContext)(block: => A): A = {
    val wasInClosure = context.isClosure
    val savedMethodContext = context.saveMethodContext()
    val collecting = context.hasReturnTypeCollector
    if (collecting) context.pushReturnTypeCollectionDepth()
    try {
      context.setClosure(true)
      block
    }finally{
      if (collecting) context.popReturnTypeCollectionDepth()
      context.setClosure(wasInClosure)
      context.restoreMethodContext(savedMethodContext)
    }
  }
  def openFrame[A](context: LocalContext)(block: => A): A = context.openFrame(block)

  def processMethodDeclaration(node: AST.MethodDeclaration): Unit =
    if (node.block != null) {
      typing.kernelNodeOf[MethodDefinition](node).foreach { method =>
        val methodTypeParams = typing.declaredTypeParams_.getOrElse(node, Seq())
        typing.openTypeParams(unitContext.currentTypeParams ++ methodTypeParams) {
          methodBodySupport.processMethodLikeBody(method, node.args, node.block)
        }
      }
    }
  def processConstructorDeclaration(node: AST.ConstructorDeclaration): Unit =
    methodBodySupport.processConstructorDeclaration(node)
  def processClassDeclaration(node: AST.ClassDeclaration, context: LocalContext): Unit = {
    declarationBodySupport.processClassDeclaration(node)
  }
  def processRecordDeclaration(node: AST.RecordDeclaration, context: LocalContext): Unit =
    // Pattern-attached records carry synthesized parse/parseAll methods (built in
    // Rewriting); type their bodies like ordinary record methods. Plain records have
    // no synthesizedMethods, so there is nothing to do.
    if (node.synthesizedMethods.nonEmpty) {
      typing.kernelNodeOf[ClassDefinition](node).foreach { definition =>
        unitContext.currentDefinition = definition
        typing.find(definition.name).foreach(unitContext.currentMapper = _)
        val recordTypeParams = typing.declaredTypeParams_.getOrElse(node, Seq())
        typing.openTypeParams(typing.emptyTypeParams ++ recordTypeParams) {
          node.synthesizedMethods.foreach(processMethodDeclaration)
        }
      }
    }
  def processInterfaceDeclaration(node: AST.InterfaceDeclaration, context: LocalContext): Unit =
    // Default methods (interface methods with bodies) get typed like
    // instance methods; signature-only declarations have nothing to do
    if (node.methods.exists(_.block != null)) {
      typing.kernelNodeOf[ClassDefinition](node).foreach { definition =>
        unitContext.currentDefinition = definition
        typing.find(definition.name).foreach(unitContext.currentMapper = _)
        val interfaceTypeParams = typing.declaredTypeParams_.getOrElse(node, Seq())
        typing.openTypeParams(typing.emptyTypeParams ++ interfaceTypeParams) {
          node.methods.foreach(processMethodDeclaration)
        }
      }
    }
  def processEnumDeclaration(node: AST.EnumDeclaration, context: LocalContext): Unit =
    // Lower each constant into a static-initializer assignment:
    //   FIELD = new EnumType("FIELD", ordinal, args...)
    // so codegen emits the <clinit> like any other static initializer.
    typing.kernelNodeOf[ClassDefinition](node).foreach { definition =>
      unitContext.currentDefinition = definition
      typing.find(definition.name).foreach(unitContext.currentMapper = _)
      val stringType = typing.loadRequired("java.lang.String")
      val ctorOpt = definition.constructors.headOption
      if (ctorOpt.isEmpty) return
      val ctor = ctorOpt.get
      val paramTypes = ctor.getArgs.drop(2)
      val enumContext = new LocalContext
      enumContext.setStatic(true)
      val statements = scala.collection.mutable.Buffer[ActionStatement]()
      node.constants.zipWithIndex.foreach { case (constant, ordinal) =>
        if (constant.args.length != paramTypes.length) {
          typing.report(
            SemanticError.CONSTRUCTOR_NOT_FOUND, constant, definition,
            constant.args.map(_ => bodyContext.rootClass: Type).toArray, definition.constructors
          )
        } else {
          val typedArgs = constant.args.zip(paramTypes).map { case (argExpr, expected) =>
            typed(argExpr, enumContext, expected).map { term =>
              assignabilitySupport.processAssignable(argExpr, expected, term)
            }.orNull
          }
          if (typedArgs.forall(_ != null)) {
            val field = definition.field(constant.name)
            val callArgs: Array[Term] =
              Array[Term](new StringValue(constant.name, stringType), new IntValue(constant.location, ordinal)) ++ typedArgs
            val init = new NewObject(ctor, callArgs)
            statements += new ExpressionActionStatement(new SetStaticField(constant.location, definition, field, init))
          }
        }
      }
      definition.setStaticInitializers(statements.toArray)

      // Type the bodies of user-defined members
      for (section <- node.sections; member <- section.members) {
        member match {
          case m: AST.MethodDeclaration => processMethodDeclaration(m)
          case _ => // enum bodies currently support methods only
        }
      }
    }

  def processExtensionDeclaration(node: AST.ExtensionDeclaration): Unit =
    declarationBodySupport.processExtensionDeclaration(node)
  def processFunctionDeclaration(node: AST.FunctionDeclaration, context: LocalContext): Unit =
    typing.kernelNodeOf[MethodDefinition](node).foreach { function =>
      if (node.block == null) {
        typing.report(SemanticError.FUNCTION_BODY_REQUIRED, node, node.name)
      } else {
        val functionTypeParams = typing.declaredTypeParams_.getOrElse(node, Seq())
        typing.openTypeParams(typing.emptyTypeParams ++ functionTypeParams) {
          methodBodySupport.processMethodLikeBody(function, node.args, node.block)
        }
      }
    }
  def processGlobalVariableDeclaration(node: AST.GlobalVariableDeclaration, context: LocalContext): Unit = {()}
  def processLocalAssign(node: AST.Assignment, context: LocalContext): Term =
    assignmentTyping.processLocalAssign(node, context)
  // Removed: processThisFieldAssign - use this.field or self.field instead
  def processArrayAssign(node: AST.Assignment, context: LocalContext): Term =
    assignmentTyping.processArrayAssign(node, context)
  def processMemberAssign(node: AST.Assignment, context: LocalContext): Term =
    assignmentTyping.processMemberAssign(node, context)
  def processEquals(kind: BinaryKind, node: AST.BinaryExpression, context: LocalContext): Term =
    operatorTyping.processEquals(kind, node, context)

  def processShiftExpression(kind: BinaryKind, node: AST.BinaryExpression, context: LocalContext): Term =
    operatorTyping.processShiftExpression(kind, node, context)

  def processComparableExpression(node: AST.BinaryExpression, context: LocalContext): Array[Term] =
    operatorTyping.processComparableExpression(node, context)

  def processBitExpression(kind: BinaryKind, node: AST.BinaryExpression, context: LocalContext): Term =
    operatorTyping.processBitExpression(kind, node, context)

  def processLogicalExpression(node: AST.BinaryExpression, context: LocalContext): Array[Term] =
    operatorTyping.processLogicalExpression(node, context)

  def processRefEquals(kind: BinaryKind, node: AST.BinaryExpression, context: LocalContext): Term =
    operatorTyping.processRefEquals(kind, node, context)
  def typedTerms(nodes: Array[AST.Expression], context: LocalContext): Array[Term] = {
    var failed = false
    val result = nodes.map{node => typed(node, context).getOrElse{failed = true; null}}
    if(failed) null else result
  }

  def typeMemberSelection(node: AST.MemberSelection, context: LocalContext): Option[Term] =
    methodCallTyping.typeMemberSelection(node, context)

  def typeMethodCall(node: AST.MethodCall, context: LocalContext, expected: Type = null): Option[Term] =
    methodCallTyping.typeMethodCall(node, context, expected)

  def typeSafeMemberSelection(node: AST.SafeMemberSelection, context: LocalContext): Option[Term] =
    methodCallTyping.typeSafeMemberSelection(node, context)

  def typeSafeMethodCall(node: AST.SafeMethodCall, context: LocalContext, expected: Type = null): Option[Term] =
    methodCallTyping.typeSafeMethodCall(node, context, expected)

  def typeUnqualifiedMethodCall(node: AST.UnqualifiedMethodCall, context: LocalContext, expected: Type = null): Option[Term] =
    methodCallTyping.typeUnqualifiedMethodCall(node, context, expected)

  def typeStaticMemberSelection(node: AST.StaticMemberSelection): Option[Term] =
    methodCallTyping.typeStaticMemberSelection(node).flatMap {
      case ref: RefStaticField if !MemberAccess.isMemberAccessible(ref.field, bodyContext.definition) =>
        bodyContext.report(FIELD_NOT_ACCESSIBLE, node, ref.target, node.name, bodyContext.definition)
        None
      case term => Some(term)
    }

  def typeStaticMethodCall(node: AST.StaticMethodCall, context: LocalContext, expected: Type = null): Option[Term] =
    methodCallTyping.typeStaticMethodCall(node, context, expected)

  def typeSuperMethodCall(node: AST.SuperMethodCall, context: LocalContext, expected: Type = null): Option[Term] =
    methodCallTyping.typeSuperMethodCall(node, context, expected)

  def typeIndexing(node: AST.Indexing, context: LocalContext): Option[Term] =
    expressionFormTyping.typeIndexing(node, context)

  def typeSafeIndexing(node: AST.SafeIndexing, context: LocalContext): Option[Term] =
    expressionFormTyping.typeSafeIndexing(node, context)

  def typeNotNullAssertion(node: AST.NotNullAssertion, context: LocalContext): Option[Term] =
    expressionFormTyping.typeNotNullAssertion(node, context)

  def typeNewArray(node: AST.NewArray, context: LocalContext): Option[Term] =
    expressionFormTyping.typeNewArray(node, context)

  def typeNewArrayWithValues(node: AST.NewArrayWithValues, context: LocalContext): Option[Term] =
    expressionFormTyping.typeNewArrayWithValues(node, context)

  def typeNewObject(node: AST.NewObject, context: LocalContext, expected: Type = null): Option[Term] =
    expressionFormTyping.typeNewObject(node, context, expected)

  def typeStringInterpolation(node: AST.StringInterpolation, context: LocalContext): Option[Term] =
    expressionFormTyping.typeStringInterpolation(node, context)

  def typeNumericBinary(node: AST.BinaryExpression, kind: BinaryKind, context: LocalContext): Option[Term] =
    operatorTyping.typeNumericBinary(node, kind, context)

  def typeLogicalBinary(node: AST.BinaryExpression, kind: BinaryKind, context: LocalContext): Option[Term] =
    operatorTyping.typeLogicalBinary(node, kind, context)

  def typeComparableBinary(node: AST.BinaryExpression, kind: BinaryKind, context: LocalContext): Option[Term] =
    operatorTyping.typeComparableBinary(node, kind, context)

  def typeUnaryIntegral(node: AST.UnaryExpression, symbol: String, kind: UnaryKind, context: LocalContext): Option[Term] =
    operatorTyping.typeUnaryIntegral(node, symbol, kind, context)

  def typeUnaryNumeric(node: AST.UnaryExpression, symbol: String, kind: UnaryKind, context: LocalContext): Option[Term] =
    operatorTyping.typeUnaryNumeric(node, symbol, kind, context)

  def typeUnaryBoolean(node: AST.UnaryExpression, symbol: String, kind: UnaryKind, context: LocalContext): Option[Term] =
    operatorTyping.typeUnaryBoolean(node, symbol, kind, context)

  def typePostUpdate(node: AST.Expression, termNode: AST.Expression, symbol: String, binaryKind: BinaryKind, context: LocalContext): Option[Term] =
    operatorTyping.typePostUpdate(node, termNode, symbol, binaryKind, context)

  def typeAssignment(node: AST.Assignment, context: LocalContext): Option[Term] =
    assignmentTyping.typeAssignment(node, context)

  def typeElvis(node: AST.Elvis, context: LocalContext): Option[Term] =
    expressionFormTyping.typeElvis(node, context)

  def typeCast(node: AST.Cast, context: LocalContext): Option[Term] =
    expressionFormTyping.typeCast(node, context)

  def typeIsInstance(node: AST.IsInstance, context: LocalContext): Option[Term] =
    expressionFormTyping.typeIsInstance(node, context)

  private[typing] def typeAdditionNode(node: AST.Addition, context: LocalContext): Option[Term] =
    additionTyping.typeAddition(node, context)

  def typed(node: AST.Expression, context: LocalContext, expected: Type = null): Option[Term] = {
    val result = expressionDispatchSupport.typed(node, context, expected)
    result.foreach(term => typing.put(node, term))
    result
  }
  def translate(node: AST.BlockElement, context: LocalContext): ActionStatement =
    blockElementLowering.translate(node, context)

  /**
   * Promote a top-level `val`/`var` declaration to a field of the script's
   * synthetic class so that top-level functions (and later statements) can see
   * it -- a bare local would be invisible outside the generated `start` method.
   * Returns the in-order field-initialisation statement, or None to fall back
   * to a plain local (no initializer). The field is non-final so `start` can
   * assign it; immutability of a top-level `val` is not strictly enforced.
   */
  def processTopLevelVarDeclaration(
    node: AST.LocalVariableDeclaration,
    klass: ClassDefinition,
    context: LocalContext
  ): Option[ActionStatement] = {
    if (node.init == null) return None
    if (klass.field(node.name) != null) return None
    // #165, local-first: translate the declaration as an ordinary local so its binding is a
    // ClosureLocalBinding — smart-cast / null narrowing then works for it within `start`,
    // exactly like inside a function. Then mirror the value into a public field so later
    // top-level functions can still reference it (the reason #165 promoted it to a field).
    val localStmt = translate(node, context)
    val binding = context.lookup(node.name)
    if (binding == null || binding.tp == BasicType.VOID) Some(localStmt)
    else {
      val fieldType = binding.tp
      val field = new FieldDefinition(node.location, AST.M_PUBLIC | AST.M_STATIC, klass, node.name, fieldType)
      klass.add(field)
      val readLocal = new RefLocal(node.location, binding.frameIndex, binding.index, fieldType)
      val setField = new ExpressionActionStatement(new SetStaticField(node.location, klass, field, readLocal))
      Some(new StatementBlock(node.location, localStmt, setField))
    }
  }

  def defaultValue(typeRef: Type): Term = Term.defaultValue(typeRef)
  def addReturnNode(node: ActionStatement, returnType: Type): StatementBlock = {
    new StatementBlock(node, new Return(defaultValue(returnType)))
  }

  private[typing] def findMethod(node: AST.Node, target: ObjectType, name: String): Method =
    methodLookupSupport.findMethod(node, target, name)
  private[typing] def findMethod(node: AST.Node, target: ObjectType, name: String, params: Array[Term]): Method =
    methodLookupSupport.findMethod(node, target, name, params)
  private[typing] def types(terms: Array[Term]): Array[Type] =
    methodLookupSupport.types(terms)
  private[typing] def typeNames(types: Array[Type]): Array[String] =
    methodLookupSupport.typeNames(types)
  private[typing] def tryFindMethod(node: AST.Node, target: ObjectType, name: String, params: Array[Term]): Either[typing.Continuable, Method] =
    methodLookupSupport.tryFindMethod(node, target, name, params)

  private def processNumericExpression(kind: BinaryKind, node: AST.BinaryExpression, lt: Term, rt: Term): Term =
    operatorTyping.processNumericExpression(kind, node, lt, rt)
  private[typing] def addArgument(arg: AST.Argument, context: LocalContext): Type =
    methodLookupSupport.addArgument(arg, context)
  private[typing] def typeClosureNode(node: AST.ClosureExpression, context: LocalContext, expected: Type): Option[Term] =
    closureTyping.typeClosure(node, context, expected)
  private[typing] def closureMatchesSam(node: AST.ClosureExpression, context: LocalContext, target: Type): Option[Boolean] =
    closureTyping.matchesSam(node, context, target)
  private[typing] def typeLocalVariableDeclarationNode(node: AST.LocalVariableDeclaration, context: LocalContext): Option[Term] = {
    val statement = blockElementLowering.translate(node, context)
    Some(new StatementTerm(node.location, statement, BasicType.VOID))
  }
  private[typing] def typeSimpleExpression(node: AST.Expression, context: LocalContext, expected: Type): Option[Term] =
    simpleExpressionTypingSupport.typeSimple(node, context, expected)

  private def runUnit(): Unit = {
    val prepared = topLevelTypingSupport.prepareUnit(unit)
    topLevelTypingSupport.processToplevels(unit.toplevels, prepared)
    topLevelTypingSupport.finishUnit(prepared)
  }
}
