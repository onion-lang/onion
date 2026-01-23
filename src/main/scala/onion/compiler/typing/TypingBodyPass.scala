package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.TypedAST.BinaryTerm.Kind as BinaryKind
import onion.compiler.TypedAST.BinaryTerm.Kind.*
import onion.compiler.TypedAST.UnaryTerm.Kind as UnaryKind
import onion.compiler.TypedAST.UnaryTerm.Kind.*
import onion.compiler.toolbox.Boxing

import scala.jdk.CollectionConverters.*
import scala.collection.mutable.{Buffer, HashMap, Map, Set => MutableSet, Stack}

final class TypingBodyPass(private val typing: Typing, private val unit: AST.CompilationUnit) {
  import typing.*
  private val methodCallTyping = new MethodCallTyping(typing, this)
  private val assignmentTyping = new AssignmentTyping(typing, this)
  private val operatorTyping = new OperatorTyping(typing, this)
  private val expressionFormTyping = new ExpressionFormTyping(typing, this)
  private val closureTyping = new ClosureTyping(typing, this)
  private val statementTyping = new StatementTyping(typing, this)
  private val controlExpressionTyping = new ControlExpressionTyping(typing, this)
  def run(): Unit = runUnit()

  def createEqualsForRef(lhs: Term, rhs: Term): Term =
    operatorTyping.createEquals(EQUAL, lhs, rhs)

  def processNodes(nodes: Array[AST.Expression], typeRef: Type, bind: ClosureLocalBinding, context: LocalContext): Term = {
    val expressions = new Array[Term](nodes.length)
    var error = false
    var i = 0
    while (i < nodes.length) {
      val expression = typed(nodes(i), context).getOrElse(null)
      expressions(i) = expression
      if (expression == null) {
        error = true
      } else if (!TypeRules.isAssignable(typeRef, expression.`type`)) {
        report(INCOMPATIBLE_TYPE, nodes(i), typeRef, expression.`type`)
        error = true
      } else {
        expressions(i) = normalizePatternTerm(expression, typeRef)
      }
      i += 1
    }
    if (error) null else buildEqualsChain(expressions, bind)
  }

  private def normalizePatternTerm(term: Term, expected: Type): Term = {
    var normalized = term
    if (normalized.isBasicType && normalized.`type` != expected) {
      normalized = new AsInstanceOf(normalized, expected)
    }
    if (normalized.isReferenceType && normalized.`type` != rootClass) {
      normalized = new AsInstanceOf(normalized, rootClass)
    }
    normalized
  }

  private def buildEqualsChain(expressions: Array[Term], bind: ClosureLocalBinding): Term = {
    val ref = new RefLocal(bind)
    var node: Term =
      if (expressions(0).isReferenceType) operatorTyping.createEquals(EQUAL, ref, expressions(0))
      else new BinaryTerm(EQUAL, BasicType.BOOLEAN, ref, expressions(0))
    var i = 1
    while (i < expressions.length) {
      node = new BinaryTerm(
        LOGICAL_OR,
        BasicType.BOOLEAN,
        node,
        new BinaryTerm(EQUAL, BasicType.BOOLEAN, ref, expressions(i))
      )
      i += 1
    }
    node
  }

  def processAssignable(node: AST.Node, expected: Type, actual: Term): Term = {
    if (actual == null) return null
    if (actual.`type`.isBottomType) return actual
    if (expected == actual.`type`) return actual

    // 1. プリミティブ型 → 参照型: オートボクシング
    if (!expected.isBasicType && actual.`type`.isBasicType) {
      val basicType = actual.`type`.asInstanceOf[BasicType]
      if (basicType == BasicType.VOID) {
        report(IS_NOT_BOXABLE_TYPE, node, basicType)
        return null
      }
      val boxed = Boxing.boxing(table_, actual)
      if (TypeRules.isAssignable(expected, boxed.`type`)) {
        return if (expected == boxed.`type`) boxed else new AsInstanceOf(node.location, boxed, expected)
      }
    }

    // 2. 参照型 → プリミティブ型: オートアンボクシング
    if (expected.isBasicType && !actual.`type`.isBasicType) {
      val targetBasicType = expected.asInstanceOf[BasicType]
      if (targetBasicType == BasicType.VOID) {
        report(INCOMPATIBLE_TYPE, node, expected, actual.`type`)
        return null
      }
      val boxedType = Boxing.boxedType(table_, targetBasicType)
      if (TypeRules.isAssignable(boxedType, actual.`type`)) {
        return Boxing.unboxing(table_, actual, targetBasicType)
      }
    }

    // 3. 型変数を含む場合の特別チェック
    // expectedがTypeVariableを含むAppliedClassTypeの場合、構造的にマッチすればOK
    def containsTypeVariable(tp: Type): Boolean = tp match {
      case _: TypedAST.TypeVariableType => true
      case applied: TypedAST.AppliedClassType => applied.typeArguments.exists(containsTypeVariable)
      case at: TypedAST.ArrayType => containsTypeVariable(at.base)
      case _ => false
    }

    def structurallyAssignable(expected: Type, actual: Type): Boolean = (expected, actual) match {
      case (tv: TypedAST.TypeVariableType, _) =>
        // 型変数は任意の型を受け入れる（上限境界を満たせば）
        TypeRules.isSuperType(tv.upperBound, actual)
      case (ae: TypedAST.AppliedClassType, aa: TypedAST.AppliedClassType) =>
        // 同じrawクラスで型引数が構造的にマッチ
        (ae.raw.name == aa.raw.name) &&
          ae.typeArguments.length == aa.typeArguments.length &&
          ae.typeArguments.zip(aa.typeArguments).forall { case (e, a) => structurallyAssignable(e, a) }
      case (ae: TypedAST.AppliedClassType, _) if containsTypeVariable(ae) =>
        // expected側に型変数があるが、actualがAppliedClassTypeでない場合
        // rawクラスが一致すればOK
        actual match {
          case ct: TypedAST.ClassType => ae.raw.name == ct.name
          case _ => false
        }
      case _ =>
        TypeRules.isAssignable(expected, actual)
    }

    // 4. AppliedClassType同士のボクシングを考慮したチェック
    def isAssignableWithBoxing(expectedType: Type, actualType: Type): Boolean =
      if (TypeRules.isAssignable(expectedType, actualType)) true
      else if (!expectedType.isBasicType && actualType.isBasicType) {
        val boxedActual = Boxing.boxedType(table_, actualType.asInstanceOf[BasicType])
        TypeRules.isAssignable(expectedType, boxedActual)
      } else if (expectedType.isBasicType && !actualType.isBasicType) {
        val boxedExpected = Boxing.boxedType(table_, expectedType.asInstanceOf[BasicType])
        TypeRules.isAssignable(boxedExpected, actualType)
      } else (expectedType, actualType) match {
        case (ae: TypedAST.AppliedClassType, aa: TypedAST.AppliedClassType) =>
          (ae.raw.name == aa.raw.name) &&
            ae.typeArguments.length == aa.typeArguments.length &&
            ae.typeArguments.zip(aa.typeArguments).forall { case (e, a) => isAssignableWithBoxing(e, a) }
        case _ => false
      }

    // 5. 通常のチェック（型変数を考慮）
    val isCompatible = if (containsTypeVariable(expected)) {
      structurallyAssignable(expected, actual.`type`)
    } else {
      isAssignableWithBoxing(expected, actual.`type`)
    }

    if (!isCompatible) {
      report(INCOMPATIBLE_TYPE, node, expected, actual.`type`)
      return null
    }
    new AsInstanceOf(node.location, actual, expected)
  }
  def openClosure[A](context: LocalContext)(block: => A): A = {
    val tmp = context.isClosure
    val savedMethodContext = context.saveMethodContext()
    val collecting = context.hasReturnTypeCollector
    if (collecting) context.pushReturnTypeCollectionDepth()
    try {
      context.setClosure(true)
      block
    }finally{
      if (collecting) context.popReturnTypeCollectionDepth()
      context.setClosure(tmp)
      context.restoreMethodContext(savedMethodContext)
    }
  }
  def openFrame[A](context: LocalContext)(block: => A): A = context.openFrame(block)

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

  /** Common logic for setting up context and processing method/function bodies */
  private def processMethodLikeBody(
    method: MethodDefinition,
    args: List[AST.Argument],
    block: AST.BlockExpression
  ): Unit = {
    val context = new LocalContext
    if ((method.modifier & AST.M_STATIC) != 0) {
      context.setStatic(true)
    }
    context.setMethod(method)
    val arguments = method.arguments

    // Scan for captured variables before processing the method body
    markCapturedVariables(context, args, block)
    bindParameters(context, args, arguments)

    // Process default argument values
    val argsWithDefaults = buildArgumentsWithDefaults(args, arguments, context)
    method.setArgumentsWithDefaults(argsWithDefaults)

    val translatedBlock = addReturnNode(translate(block, context).asInstanceOf[StatementBlock], method.returnType)
    method.setBlock(translatedBlock)
    method.setFrame(context.getContextFrame)

    // Report unused variable warnings
    reportUnused(context)
  }

  def processMethodDeclaration(node: AST.MethodDeclaration): Unit = {
    val method = lookupKernelNode(node).asInstanceOf[MethodDefinition]
    if (method == null) return
    if (node.block == null) return
    val methodTypeParams = declaredTypeParams_.getOrElse(node, Seq())
    openTypeParams(typeParams_ ++ methodTypeParams) {
      processMethodLikeBody(method, node.args, node.block)
    }
  }
  def processConstructorDeclaration(node: AST.ConstructorDeclaration): Unit = {
    val constructor = lookupKernelNode(node).asInstanceOf[ConstructorDefinition]
    if (constructor == null) return
    val context = new LocalContext
    context.setConstructor(constructor)
    val args = constructor.getArgs
    bindParameters(context, node.args, args)
    val params = typedTerms(node.superInits.toArray, context)
    val currentClass = definition_
    val superClass = currentClass.superClass
    val matched = superClass.findConstructor(params)
    if (matched.length == 0) {
      report(CONSTRUCTOR_NOT_FOUND, node, superClass, types(params), superClass.constructors)
    }else if (matched.length > 1) {
      report(AMBIGUOUS_CONSTRUCTOR, node, Array[AnyRef](superClass, types(params)), Array[AnyRef](superClass, types(params)))
    }else {
      val init = new Super(superClass, matched(0).getArgs, params)
      val block = addReturnNode(translate(node.block, context).asInstanceOf[StatementBlock], BasicType.VOID)
      constructor.superInitializer = init
      constructor.block = block
      constructor.frame = context.getContextFrame
    }

    // Report unused variable warnings
    reportUnused(context)
  }
  def processClassDeclaration(node: AST.ClassDeclaration, context: LocalContext): Unit = {
    definition_ = lookupKernelNode(node).asInstanceOf[ClassDefinition]
    mapper_ = find(definition_.name)
    val classTypeParams = declaredTypeParams_.getOrElse(node, Seq())
    openTypeParams(emptyTypeParams ++ classTypeParams) {
      val instanceInitializers = Buffer[ActionStatement]()
      val staticInitializers = Buffer[ActionStatement]()
      for (section <- node.defaultSection ++ node.sections; member <- section.members) {
        member match {
          case field: AST.FieldDeclaration =>
            collectFieldInitializer(field, instanceInitializers, staticInitializers)
          case member: AST.MethodDeclaration =>
            processMethodDeclaration(member)
          case member: AST.ConstructorDeclaration =>
            processConstructorDeclaration(member)
          case field: AST.DelegatedFieldDeclaration =>
            collectDelegatedFieldInitializer(field, instanceInitializers, staticInitializers)
        }
      }
      injectInstanceInitializers(definition_, instanceInitializers.toSeq)
      if (staticInitializers.nonEmpty) {
        definition_.setStaticInitializers(staticInitializers.toArray)
      }
    }
  }
  def processInterfaceDeclaration(node: AST.InterfaceDeclaration, context: LocalContext): Unit = { () }
  def processEnumDeclaration(node: AST.EnumDeclaration, context: LocalContext): Unit = { () }
  def processFunctionDeclaration(node: AST.FunctionDeclaration, context: LocalContext): Unit = {
    val function = lookupKernelNode(node).asInstanceOf[MethodDefinition]
    if (function == null) return
    processMethodLikeBody(function, node.args, node.block)
  }
  private def collectFieldInitializer(
    node: AST.FieldDeclaration,
    instanceInitializers: Buffer[ActionStatement],
    staticInitializers: Buffer[ActionStatement]
  ): Unit = {
    if (node.init == null) return
    val field = lookupKernelNode(node).asInstanceOf[FieldDefinition]
    if (field == null) return
    val context = new LocalContext
    val isStatic = Modifier.isStatic(node.modifiers)
    context.setStatic(isStatic)
    val fieldType = field.`type`
    typed(node.init, context, fieldType) match {
      case Some(term) =>
        val value = processAssignable(node.init, fieldType, term)
        if (value != null) {
          val statement =
            if (isStatic) new ExpressionActionStatement(new SetStaticField(definition_, field, value))
            else new ExpressionActionStatement(new SetField(new This(definition_), field, value))
          if (isStatic) staticInitializers += statement else instanceInitializers += statement
        }
      case None => ()
    }
  }

  private def collectDelegatedFieldInitializer(
    node: AST.DelegatedFieldDeclaration,
    instanceInitializers: Buffer[ActionStatement],
    staticInitializers: Buffer[ActionStatement]
  ): Unit = {
    if (node.init == null) return
    val field = lookupKernelNode(node).asInstanceOf[FieldDefinition]
    if (field == null) return
    val context = new LocalContext
    val isStatic = Modifier.isStatic(node.modifiers)
    context.setStatic(isStatic)
    val fieldType = field.`type`
    typed(node.init, context, fieldType) match {
      case Some(term) =>
        val value = processAssignable(node.init, fieldType, term)
        if (value != null) {
          val statement =
            if (isStatic) new ExpressionActionStatement(new SetStaticField(definition_, field, value))
            else new ExpressionActionStatement(new SetField(new This(definition_), field, value))
          if (isStatic) staticInitializers += statement else instanceInitializers += statement
        }
      case None => ()
    }
  }

  private def injectInstanceInitializers(classDef: ClassDefinition, initializers: Seq[ActionStatement]): Unit = {
    if (initializers.isEmpty) return
    classDef.constructors.foreach {
      case ctor: ConstructorDefinition =>
        val existing = Option(ctor.block).map(_.statements.toIndexedSeq).getOrElse(Seq.empty)
        val combined = (initializers ++ existing).toArray
        ctor.block = new StatementBlock(combined: _*)
      case _ => ()
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

  def typeUnqualifiedMethodCall(node: AST.UnqualifiedMethodCall, context: LocalContext, expected: Type = null): Option[Term] =
    methodCallTyping.typeUnqualifiedMethodCall(node, context, expected)

  def typeStaticMemberSelection(node: AST.StaticMemberSelection): Option[Term] =
    methodCallTyping.typeStaticMemberSelection(node)

  def typeStaticMethodCall(node: AST.StaticMethodCall, context: LocalContext, expected: Type = null): Option[Term] =
    methodCallTyping.typeStaticMethodCall(node, context, expected)

  def typeSuperMethodCall(node: AST.SuperMethodCall, context: LocalContext, expected: Type = null): Option[Term] =
    methodCallTyping.typeSuperMethodCall(node, context, expected)

  def typeIndexing(node: AST.Indexing, context: LocalContext): Option[Term] =
    expressionFormTyping.typeIndexing(node, context)

  def typeNewArray(node: AST.NewArray, context: LocalContext): Option[Term] =
    expressionFormTyping.typeNewArray(node, context)

  def typeNewArrayWithValues(node: AST.NewArrayWithValues, context: LocalContext): Option[Term] =
    expressionFormTyping.typeNewArrayWithValues(node, context)

  def typeNewObject(node: AST.NewObject, context: LocalContext): Option[Term] =
    expressionFormTyping.typeNewObject(node, context)

  def typeStringInterpolation(node: AST.StringInterpolation, context: LocalContext): Option[Term] =
    expressionFormTyping.typeStringInterpolation(node, context)

  def typeBinaryAssignment(node: AST.Expression, lhs: AST.Expression, rhs: AST.Expression, binaryKind: BinaryKind, context: LocalContext): Option[Term] = {
    // Desugar: a += b becomes a = a + b
    // First, create the binary operation
    val binaryOp = binaryKind match {
      case ADD => new AST.Addition(node.location, lhs, rhs)
      case SUBTRACT => new AST.Subtraction(node.location, lhs, rhs)
      case MULTIPLY => new AST.Multiplication(node.location, lhs, rhs)
      case DIVIDE => new AST.Division(node.location, lhs, rhs)
      case MOD => new AST.Modulo(node.location, lhs, rhs)
      case BIT_AND => new AST.BitAnd(node.location, lhs, rhs)
      case BIT_OR => new AST.BitOr(node.location, lhs, rhs)
      case XOR => new AST.XOR(node.location, lhs, rhs)
      case BIT_SHIFT_L2 => new AST.MathLeftShift(node.location, lhs, rhs)
      case BIT_SHIFT_R2 => new AST.MathRightShift(node.location, lhs, rhs)
      case BIT_SHIFT_R3 => new AST.LogicalRightShift(node.location, lhs, rhs)
      case _ => throw new IllegalArgumentException(s"Invalid compound assignment operator: $binaryKind")
    }

    // Then create the assignment: a = (a + b)
    val assignment = new AST.Assignment(node.location, lhs, binaryOp)

    // Type-check the assignment
    typeAssignment(assignment, context)
  }

  def typeNumericBinary(node: AST.BinaryExpression, kind: BinaryKind, context: LocalContext): Option[Term] =
    operatorTyping.typeNumericBinary(node, kind, context)

  def typeLogicalBinary(node: AST.BinaryExpression, kind: BinaryKind, context: LocalContext): Option[Term] =
    operatorTyping.typeLogicalBinary(node, kind, context)

  def typeComparableBinary(node: AST.BinaryExpression, kind: BinaryKind, context: LocalContext): Option[Term] =
    operatorTyping.typeComparableBinary(node, kind, context)

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

  private def typeAddition(node: AST.Addition, context: LocalContext): Option[Term] = {
    val left = typed(node.lhs, context).getOrElse(null)
    val right = typed(node.rhs, context).getOrElse(null)
    if (left == null || right == null) return None

    tryNumericAddition(node, left, right).orElse(typeStringConcatenation(node, left, right))
  }

  private def tryNumericAddition(node: AST.Addition, left: Term, right: Term): Option[Term] =
    (numericBasicType(left), numericBasicType(right)) match {
      case (Some(lbt), Some(rbt)) =>
        val leftTerm = if (left.isBasicType) left else Boxing.unboxing(table_, left, lbt)
        val rightTerm = if (right.isBasicType) right else Boxing.unboxing(table_, right, rbt)
        Some(processNumericExpression(ADD, node, leftTerm, rightTerm))
      case _ => None
    }

  private def typeStringConcatenation(node: AST.Addition, left: Term, right: Term): Option[Term] = {
    val leftBoxed = boxForConcat(node.lhs, left)
    val rightBoxed = boxForConcat(node.rhs, right)
    if (leftBoxed.isEmpty || rightBoxed.isEmpty) return None

    val leftString = toStringCall(node.lhs, leftBoxed.get)
    val rightString = toStringCall(node.rhs, rightBoxed.get)
    val concat = findMethod(node, leftString.`type`.asInstanceOf[ObjectType], "concat", Array[Term](rightString))
    Some(new Call(leftString, concat, Array[Term](rightString)))
  }

  private def boxForConcat(node: AST.Expression, term: Term): Option[Term] =
    if (!term.isBasicType) Some(term)
    else if (term.`type` == BasicType.VOID) {
      report(IS_NOT_BOXABLE_TYPE, node, term.`type`)
      None
    } else {
      Some(Boxing.boxing(table_, term))
    }

  private def toStringCall(node: AST.Expression, term: Term): Term = {
    val toStringMethod = findMethod(node, term.`type`.asInstanceOf[ObjectType], "toString")
    new Call(term, toStringMethod, Array.empty)
  }

  private def numericBasicType(term: Term): Option[BasicType] = {
    if (term.isBasicType) {
      val bt = term.`type`.asInstanceOf[BasicType]
      if (isNumeric(bt)) Some(bt) else None
    } else {
      Boxing.unboxedType(table_, term.`type`).filter(isNumeric)
    }
  }

  private def isNumeric(t: BasicType): Boolean =
    (t eq BasicType.BYTE) || (t eq BasicType.SHORT) || (t eq BasicType.CHAR) ||
      (t eq BasicType.INT) || (t eq BasicType.LONG) || (t eq BasicType.FLOAT) ||
      (t eq BasicType.DOUBLE)

  def typed(node: AST.Expression, context: LocalContext, expected: Type = null): Option[Term] = node match {
    case node: AST.Addition =>
      typeAddition(node, context)
    case node@AST.Subtraction(loc, left, right) =>
      typeNumericBinary(node, SUBTRACT, context)
    case node@AST.Multiplication(loc, left, right) =>
      typeNumericBinary(node, MULTIPLY, context)
    case node@AST.Division(loc, left, right) =>
      typeNumericBinary(node, DIVIDE, context)
    case node@AST.Modulo(loc, left, right) =>
      typeNumericBinary(node, MOD, context)
    case node: AST.Assignment =>
      typeAssignment(node, context)
    case node@AST.LogicalAnd(loc, left, right) =>
      typeLogicalBinary(node, LOGICAL_AND, context)
    case node@AST.LogicalOr(loc, left, right) =>
      typeLogicalBinary(node, LOGICAL_OR, context)
    case node@AST.BitAnd(loc, l, r) =>
      Option(processBitExpression(BIT_AND, node, context))
    case node@AST.BitOr(loc, l, r) =>
      Option(processBitExpression(BIT_OR, node, context))
    case node@AST.XOR(loc, left, right) =>
      Option(processBitExpression(XOR, node, context))
    case node@AST.LogicalRightShift(loc, left, right) =>
      Option(processShiftExpression(BIT_SHIFT_R3, node, context))
    case node@AST.MathLeftShift(loc, left, right) =>
      Option(processShiftExpression(BIT_SHIFT_L2, node, context))
    case node@AST.MathRightShift(loc, left, right) =>
      Option(processShiftExpression(BIT_SHIFT_R2, node, context))
    case node@AST.GreaterOrEqual(loc, left, right) =>
      typeComparableBinary(node, GREATER_OR_EQUAL, context)
    case node@AST.GreaterThan(loc, left, right) =>
      typeComparableBinary(node, GREATER_THAN, context)
    case node@AST.LessOrEqual(loc, left, right) =>
      typeComparableBinary(node, LESS_OR_EQUAL, context)
    case node@AST.LessThan(loc, left, right) =>
      typeComparableBinary(node, LESS_THAN, context)
    case node@AST.Equal(loc, left, right) =>
      Option(processEquals(EQUAL, node, context))
    case node@AST.NotEqual(loc, left, right) =>
      Option(processEquals(NOT_EQUAL, node, context))
    case node@AST.ReferenceEqual(loc, left, right) =>
      Option(processRefEquals(EQUAL, node, context))
    case node@AST.ReferenceNotEqual(loc, left, right) =>
      Option(processRefEquals(NOT_EQUAL, node, context))
    case node: AST.Elvis =>
      typeElvis(node, context)
    case node: AST.Indexing =>
      typeIndexing(node, context)
    case node@AST.AdditionAssignment(_, left, right) =>
      typeBinaryAssignment(node, left, right, ADD, context)
    case node@AST.SubtractionAssignment(_, left, right) =>
      typeBinaryAssignment(node, left, right, SUBTRACT, context)
    case node@AST.MultiplicationAssignment(_, left, right) =>
      typeBinaryAssignment(node, left, right, MULTIPLY, context)
    case node@AST.DivisionAssignment(_, left, right) =>
      typeBinaryAssignment(node, left, right, DIVIDE, context)
    case node@AST.ModuloAssignment(_, left, right) =>
      typeBinaryAssignment(node, left, right, MOD, context)
    case node@AST.BitAndAssignment(_, left, right) =>
      typeBinaryAssignment(node, left, right, BIT_AND, context)
    case node@AST.BitOrAssignment(_, left, right) =>
      typeBinaryAssignment(node, left, right, BIT_OR, context)
    case node@AST.XorAssignment(_, left, right) =>
      typeBinaryAssignment(node, left, right, XOR, context)
    case node@AST.LeftShiftAssignment(_, left, right) =>
      typeBinaryAssignment(node, left, right, BIT_SHIFT_L2, context)
    case node@AST.MathRightShiftAssignment(_, left, right) =>
      typeBinaryAssignment(node, left, right, BIT_SHIFT_R2, context)
    case node@AST.LogicalRightShiftAssignment(_, left, right) =>
      typeBinaryAssignment(node, left, right, BIT_SHIFT_R3, context)
    case node@AST.CharacterLiteral(loc, v) =>
      Some(new CharacterValue(loc, v))
    case node@AST.ByteLiteral(loc, v) =>
      Some(new ByteValue(loc, v))
    case node@AST.ShortLiteral(loc, v) =>
      Some(new ShortValue(loc, v))
    case node@AST.IntegerLiteral(loc, v) =>
      Some(new IntValue(loc, v))
    case node@AST.LongLiteral(loc, v) =>
      Some(new LongValue(loc, v))
    case node@AST.FloatLiteral(loc, v) =>
      Some(new FloatValue(loc, v))
    case node@AST.DoubleLiteral(loc, v) =>
      Some(new DoubleValue(loc, v))
    case node@AST.BooleanLiteral(loc, v) =>
      Some(new BoolValue(loc, v))
    case node@AST.ListLiteral(loc, elements) =>
      val typedElements = elements.map { e => typed(e, context).getOrElse(null) }
      if (typedElements.exists(_ == null)) {
        None
      } else {
        // Box basic types for list element type calculation
        val elementTypes = typedElements.map { t =>
          t.`type` match {
            case bt: BasicType => Boxing.boxedType(table_, bt)
            case other => other
          }
        }
        val elementType: Type = if (elementTypes.isEmpty) {
          rootClass // Empty list -> List<Object>
        } else {
          // Find least upper bound of all element types
          elementTypes.reduce { (left, right) =>
            if (left == null || right == null) null
            else if (left.isBottomType) right
            else if (right.isBottomType) left
            else if (left eq right) left
            else if (left.isNullType) right
            else if (right.isNullType) left
            else if (TypeRules.isSuperType(left, right)) left
            else if (TypeRules.isSuperType(right, left)) right
            else if (!left.isBasicType && !right.isBasicType) rootClass
            else null
          }
        }
        if (elementType == null) {
          report(INCOMPATIBLE_TYPE, node, elementTypes.head, elementTypes.last)
          None
        } else {
          val listType = AppliedClassType(load("java.util.List"), scala.collection.immutable.List(elementType))
          Some(new ListLiteral(typedElements.toArray, listType))
        }
      }
    case node@AST.NullLiteral(loc) =>
      Some(new NullValue(loc))
    case node: AST.Cast =>
      typeCast(node, context)
    case node: AST.ClosureExpression =>
      closureTyping.typeClosure(node, context, expected)
    case node@AST.CurrentInstance(loc) =>
      if (context.isStatic) {
        report(CURRENT_INSTANCE_NOT_AVAILABLE, node)
        None
      } else {
        Some(new This(loc, definition_))
      }
    case node@AST.Id(loc, name) =>
      val bind = context.lookup(name)
      if (bind == null) {
        report(VARIABLE_NOT_FOUND, node, node.name, context.allNames.toArray)
        None
      }else {
        context.recordUsage(name)
        Some(new RefLocal(bind))
      }
    case node: AST.UnqualifiedFieldReference =>
      if (context.isStatic) {
        report(VARIABLE_NOT_FOUND, node, node.name, context.allNames.toArray)
        None
      } else {
        val selection = AST.MemberSelection(node.location, AST.CurrentInstance(node.location), node.name)
        typeMemberSelection(selection, context)
      }
    case node: AST.IsInstance =>
      typeIsInstance(node, context)
    case node: AST.MemberSelection =>
      typeMemberSelection(node, context)
    case node: AST.MethodCall =>
      typeMethodCall(node, context, expected)
    case node@AST.Negate(loc, target) =>
      typeUnaryNumeric(node, "-", MINUS, context)
    case node: AST.NewArray =>
      typeNewArray(node, context)
    case node: AST.NewArrayWithValues =>
      typeNewArrayWithValues(node, context)
    case node: AST.NewObject =>
      typeNewObject(node, context)
    case node@AST.Not(loc, target) =>
      typeUnaryBoolean(node, "!", NOT, context)
    case node@AST.Posit(loc, target) =>
      typeUnaryNumeric(node, "+", PLUS, context)
    case node@AST.PostDecrement(loc, target) =>
      typePostUpdate(node, node.term, "--", SUBTRACT, context)
    case node@AST.PostIncrement(loc, target) =>
      typePostUpdate(node, node.term, "++", ADD, context)
    // Prefer this.field or self.field for field access; unqualified form is handled above for compatibility.
    case node: AST.UnqualifiedMethodCall =>
      typeUnqualifiedMethodCall(node, context, expected)
    case node: AST.StaticMemberSelection =>
      typeStaticMemberSelection(node)
    case node: AST.StaticMethodCall =>
      typeStaticMethodCall(node, context, expected)
    case node@AST.StringLiteral(loc, value) =>
      Some(new StringValue(loc, value, load("java.lang.String")))
    case node: AST.StringInterpolation =>
      typeStringInterpolation(node, context)
    case node: AST.SuperMethodCall =>
      typeSuperMethodCall(node, context, expected)
    case node: AST.NamedArgument =>
      // NamedArgumentはメソッド呼び出しのコンテキストで処理される
      // ここでは内部の値を型付けして返す
      typed(node.value, context, expected)
    case node: AST.BlockExpression =>
      controlExpressionTyping.typeBlockExpression(node, context)
    case node: AST.BreakExpression =>
      controlExpressionTyping.typeBreakExpression(node, context)
    case node: AST.ContinueExpression =>
      controlExpressionTyping.typeContinueExpression(node, context)
    case node: AST.EmptyExpression =>
      controlExpressionTyping.typeEmptyExpression(node, context)
    case node: AST.ExpressionBox =>
      controlExpressionTyping.typeExpressionBox(node, context)
    case node: AST.ForeachExpression =>
      controlExpressionTyping.typeForeachExpression(node, context)
    case node: AST.ForExpression =>
      controlExpressionTyping.typeForExpression(node, context)
    case node: AST.IfExpression =>
      controlExpressionTyping.typeIfExpression(node, context)
    case node: AST.LocalVariableDeclaration =>
      val statement = statementTyping.translate(node, context)
      Some(new StatementTerm(node.location, statement, BasicType.VOID))
    case node: AST.ReturnExpression =>
      controlExpressionTyping.typeReturnExpression(node, context)
    case node: AST.SelectExpression =>
      controlExpressionTyping.typeSelectExpression(node, context)
    case node: AST.SynchronizedExpression =>
      controlExpressionTyping.typeSynchronizedExpression(node, context)
    case node: AST.ThrowExpression =>
      controlExpressionTyping.typeThrowExpression(node, context)
    case node: AST.TryExpression =>
      controlExpressionTyping.typeTryExpression(node, context)
    case node: AST.WhileExpression =>
      controlExpressionTyping.typeWhileExpression(node, context)
  }
  def translate(node: AST.CompoundExpression, context: LocalContext): ActionStatement =
    statementTyping.translate(node, context)

  def defaultValue(typeRef: Type): Term = Term.defaultValue(typeRef)
  def addReturnNode(node: ActionStatement, returnType: Type): StatementBlock = {
    new StatementBlock(node, new Return(defaultValue(returnType)))
  }
  def createMain(top: ClassType, ref: Method, name: String, args: Array[Type], ret: Type): MethodDefinition = {
    val method = new MethodDefinition(null, AST.M_STATIC | AST.M_PUBLIC, top, name, args, ret, null)
    val frame = new LocalFrame(null)
    val params = new Array[Term](args.length)
    for(i <- 0 until args.length) {
      val arg = args(i)
      val index = frame.add("args" + i, arg)
      params(i) = new RefLocal(0, index, arg)
    }
    method.setFrame(frame)
    val constructor = top.findConstructor(new Array[Term](0))(0)
    var block = new StatementBlock(new ExpressionActionStatement(new Call(new NewObject(constructor, new Array[Term](0)), ref, params)))
    block = addReturnNode(block, BasicType.VOID)
    method.setBlock(block)
    method
  }

  private[typing] def findMethod(node: AST.Node, target: ObjectType, name: String): Method =
    findMethod(node, target, name, new Array[Term](0))
  private[typing] def findMethod(node: AST.Node, target: ObjectType, name: String, params: Array[Term]): Method = {
    val methods = MethodResolution.findMethods(target, name, params, table_)
    if (methods.length == 0) {
      report(METHOD_NOT_FOUND, node, target, name, params.map{param => param.`type`})
      return null
    }
    methods(0)
  }
  private[typing] def types(terms: Array[Term]): Array[Type] = terms.map(term => term.`type`)
  private[typing] def typeNames(types: Array[Type]): Array[String] = types.map(_.name)
  private[typing] def tryFindMethod(node: AST.Node, target: ObjectType, name: String, params: Array[Term]): Either[Continuable, Method] = {
    val methods = MethodResolution.findMethods(target, name, params, table_)
    if (methods.length > 0) {
      if (methods.length > 1) {
        report(AMBIGUOUS_METHOD, node, Array[AnyRef](methods(0).affiliation, name, methods(0).arguments), Array[AnyRef](methods(1).affiliation, name, methods(1).arguments))
        Left(false)
      } else if (!MemberAccess.isMemberAccessible(methods(0), definition_)) {
        report(METHOD_NOT_ACCESSIBLE, node, methods(0).affiliation, name, methods(0).arguments, definition_)
        Left(false)
      } else {
        Right(methods(0))
      }
    }else {
      Left(true)
    }
  }

  private def processNumericExpression(kind: BinaryKind, node: AST.BinaryExpression, lt: Term, rt: Term): Term =
    operatorTyping.processNumericExpression(kind, node, lt, rt)
  private[typing] def addArgument(arg: AST.Argument, context: LocalContext): Type = {
    val name = arg.name
    val binding = context.lookupOnlyCurrentScope(name)
    if (binding != null) {
      report(DUPLICATE_LOCAL_VARIABLE, arg, name)
      return null
    }
    val argType = mapFrom(arg.typeRef, mapper_)
    if(argType == null) return null
    context.add(name, argType)
    argType
  }

  private def runUnit(): Unit = {
    unit_ = unit
    val toplevels = unit.toplevels
    val context = new LocalContext
    val statements = Buffer[ActionStatement]()
    mapper_ = find(topClass)
    val klass = loadTopClass.asInstanceOf[ClassDefinition]
    val argsType = loadArray(load("java.lang.String"), 1)
    val method = new MethodDefinition(unit.location, AST.M_PUBLIC, klass, "start", Array[Type](argsType), BasicType.VOID, null)
    context.add("args", argsType)
    for (element <- toplevels) {
      if (!element.isInstanceOf[AST.TypeDeclaration]) definition_ = klass
      element match {
        case node: AST.CompoundExpression =>
          context.setMethod(method)
          statements += translate(node, context)
        case _ =>
          element match {
            case node: AST.ClassDeclaration => processClassDeclaration(node, context)
            case node: AST.InterfaceDeclaration => processInterfaceDeclaration(node, context)
            case node: AST.EnumDeclaration => processEnumDeclaration(node, context)
            case node: AST.FunctionDeclaration => processFunctionDeclaration(node, context)
            case node: AST.GlobalVariableDeclaration => processGlobalVariableDeclaration(node, context)
            case _ =>
          }
      }
    }
    if (klass != null) {
      statements += new Return(null)
      method.setBlock(new StatementBlock(statements.asJava))
      method.setFrame(context.getContextFrame)
      klass.add(method)
      klass.add(createMain(klass, method, "main", Array[Type](argsType), BasicType.VOID))
    }
  }
}
