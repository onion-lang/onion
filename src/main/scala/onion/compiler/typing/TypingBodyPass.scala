package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.TypedAST.BinaryTerm.Constants.*
import onion.compiler.TypedAST.UnaryTerm.Constants.*
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

  def processNodes(nodes: Array[AST.Expression], typeRef: Type, bind: ClosureLocalBinding, context: LocalContext): Term = {
    val expressions = new Array[Term](nodes.length)
    var error: Boolean = false
    for(i <- 0 until nodes.length){
      val expressionOpt = typed(nodes(i), context)
      expressions(i) = expressionOpt.getOrElse(null)
      if(expressions(i) == null) {
        error = true
      } else if (!TypeRules.isAssignable(typeRef, expressions(i).`type`)) {
        report(INCOMPATIBLE_TYPE, nodes(i), typeRef, expressions(i).`type`)
        error = true
      } else {
        if (expressions(i).isBasicType && expressions(i).`type` != typeRef) expressions(i) = new AsInstanceOf(expressions(i), typeRef)
        if (expressions(i).isReferenceType && expressions(i).`type` != rootClass) expressions(i) = new AsInstanceOf(expressions(i), rootClass)
      }
    }
    if (!error) {
      var node: Term = if(expressions(0).isReferenceType) {
        operatorTyping.createEquals(BinaryTerm.Constants.EQUAL, new RefLocal(bind), expressions(0))
      } else {
        new BinaryTerm(EQUAL, BasicType.BOOLEAN, new RefLocal(bind), expressions(0))
      }
      for(i <- 1 until expressions.length) {
        node = new BinaryTerm(LOGICAL_OR, BasicType.BOOLEAN, node, new BinaryTerm(EQUAL, BasicType.BOOLEAN, new RefLocal(bind), expressions(i)))
      }
      node
    } else {
      null
    }
  }
  def processAssignable(node: AST.Node, a: Type, b: Term): Term = {
    if (b == null) return null
    if (b.`type`.isBottomType) return b
    if (a == b.`type`) return b

    // 1. プリミティブ型 → 参照型: オートボクシング
    if (!a.isBasicType && b.`type`.isBasicType) {
      val basicType = b.`type`.asInstanceOf[BasicType]
      if (basicType == BasicType.VOID) {
        report(IS_NOT_BOXABLE_TYPE, node, basicType)
        return null
      }
      val boxed = Boxing.boxing(table_, b)
      if (TypeRules.isAssignable(a, boxed.`type`)) {
        return if (a == boxed.`type`) boxed else new AsInstanceOf(node.location, boxed, a)
      }
    }

    // 2. 参照型 → プリミティブ型: オートアンボクシング
    if (a.isBasicType && !b.`type`.isBasicType) {
      val targetBasicType = a.asInstanceOf[BasicType]
      if (targetBasicType == BasicType.VOID) {
        report(INCOMPATIBLE_TYPE, node, a, b.`type`)
        return null
      }
      val boxedType = Boxing.boxedType(table_, targetBasicType)
      if (TypeRules.isAssignable(boxedType, b.`type`)) {
        return Boxing.unboxing(table_, b, targetBasicType)
      }
    }

    // 3. 既存のチェック
    if (!TypeRules.isAssignable(a, b.`type`)) {
      report(INCOMPATIBLE_TYPE, node, a, b.`type`)
      return null
    }
    new AsInstanceOf(node.location, b, a)
  }
  def openClosure[A](context: LocalContext)(block: => A): A = {
    val tmp = context.isClosure
    try {
      context.setClosure(true)
      block
    }finally{
      context.setClosure(tmp)
    }
  }
  def openFrame[A](context: LocalContext)(block: => A): A = context.openFrame(block)
  def processMethodDeclaration(node: AST.MethodDeclaration): Unit = {
    val method = lookupKernelNode(node).asInstanceOf[MethodDefinition]
    if (method == null) return
    if (node.block == null) return
    val methodTypeParams = declaredTypeParams_.getOrElse(node, Seq())
    openTypeParams(typeParams_ ++ methodTypeParams) {
      val context = new LocalContext
      if((method.modifier & AST.M_STATIC) != 0) {
        context.setStatic(true)
      }
      context.setMethod(method)
      val arguments = method.arguments

      // Scan for captured variables before processing the method body
      val paramNames = node.args.map(_.name).toSet
      val capturedVars = CapturedVariableScanner.scan(node.block, paramNames)
      context.markAsBoxed(capturedVars)

      for(i <- 0 until arguments.length) {
        context.add(node.args(i).name, arguments(i))
      }
      val block = addReturnNode(translate(node.block, context).asInstanceOf[StatementBlock], method.returnType)
      method.setBlock(block)
      method.setFrame(context.getContextFrame)
    }
  }
  def processConstructorDeclaration(node: AST.ConstructorDeclaration): Unit = {
    val constructor = lookupKernelNode(node).asInstanceOf[ConstructorDefinition]
    if (constructor == null) return
    val context = new LocalContext
    context.setConstructor(constructor)
    val args = constructor.getArgs
    for(i <- 0 until args.length) {
      context.add(node.args(i).name, args(i))
    }
    val params = typedTerms(node.superInits.toArray, context)
    val currentClass = definition_
    val superClass = currentClass.superClass
    val matched = superClass.findConstructor(params)
    if (matched.length == 0) {
      report(CONSTRUCTOR_NOT_FOUND, node, superClass, types(params))
    }else if (matched.length > 1) {
      report(AMBIGUOUS_CONSTRUCTOR, node, Array[AnyRef](superClass, types(params)), Array[AnyRef](superClass, types(params)))
    }else {
      val init = new Super(superClass, matched(0).getArgs, params)
      val block = addReturnNode(translate(node.block, context).asInstanceOf[StatementBlock], BasicType.VOID)
      constructor.superInitializer = init
      constructor.block = block
      constructor.frame = context.getContextFrame
    }
  }
  def processClassDeclaration(node: AST.ClassDeclaration, context: LocalContext): Unit = {
    definition_ = lookupKernelNode(node).asInstanceOf[ClassDefinition]
    mapper_ = find(definition_.name)
    val classTypeParams = declaredTypeParams_.getOrElse(node, Seq())
    openTypeParams(emptyTypeParams ++ classTypeParams) {
      for(section <- node.defaultSection; member <- section.members) {
        member match {
          case member: AST.FieldDeclaration =>
          case member: AST.MethodDeclaration =>
            processMethodDeclaration(member)
          case member: AST.ConstructorDeclaration =>
            processConstructorDeclaration(member)
          case member: AST.DelegatedFieldDeclaration =>
        }
      }
      for(section <- node.sections; member <- section.members) {
        member match {
          case member: AST.FieldDeclaration =>
          case member: AST.MethodDeclaration =>
            processMethodDeclaration(member)
          case member: AST.ConstructorDeclaration =>
            processConstructorDeclaration(member)
          case member: AST.DelegatedFieldDeclaration =>
        }
      }
    }
  }
  def processInterfaceDeclaration(node: AST.InterfaceDeclaration, context: LocalContext): Unit = { () }
  def processFunctionDeclaration(node: AST.FunctionDeclaration, context: LocalContext): Unit = {
    val function = lookupKernelNode(node).asInstanceOf[MethodDefinition]
    if (function == null) return
    val context = new LocalContext
    if ((function.modifier & AST.M_STATIC) != 0) {
      context.setStatic(true)
    }
    context.setMethod(function)
    val arguments = function.arguments

    // Scan for captured variables before processing the function body
    val paramNames = node.args.map(_.name).toSet
    val capturedVars = CapturedVariableScanner.scan(node.block, paramNames)
    context.markAsBoxed(capturedVars)

    for(i <- 0 until arguments.length) {
      context.add(node.args(i).name, arguments(i))
    }
    val block = addReturnNode(translate(node.block, context).asInstanceOf[StatementBlock], function.returnType)
    function.setBlock(block)
    function.setFrame(context.getContextFrame)
  }
  def processGlobalVariableDeclaration(node: AST.GlobalVariableDeclaration, context: LocalContext): Unit = {()}
  def processLocalAssign(node: AST.Assignment, context: LocalContext): Term =
    assignmentTyping.processLocalAssign(node, context)
  // Removed: processThisFieldAssign - use this.field or self.field instead
  def processArrayAssign(node: AST.Assignment, context: LocalContext): Term =
    assignmentTyping.processArrayAssign(node, context)
  def processMemberAssign(node: AST.Assignment, context: LocalContext): Term =
    assignmentTyping.processMemberAssign(node, context)
  def processEquals(kind: Int, node: AST.BinaryExpression, context: LocalContext): Term =
    operatorTyping.processEquals(kind, node, context)

  def processShiftExpression(kind: Int, node: AST.BinaryExpression, context: LocalContext): Term =
    operatorTyping.processShiftExpression(kind, node, context)

  def processComparableExpression(node: AST.BinaryExpression, context: LocalContext): Array[Term] =
    operatorTyping.processComparableExpression(node, context)

  def processBitExpression(kind: Int, node: AST.BinaryExpression, context: LocalContext): Term =
    operatorTyping.processBitExpression(kind, node, context)

  def processLogicalExpression(node: AST.BinaryExpression, context: LocalContext): Array[Term] =
    operatorTyping.processLogicalExpression(node, context)

  def processRefEquals(kind: Int, node: AST.BinaryExpression, context: LocalContext): Term =
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

  def typeNewObject(node: AST.NewObject, context: LocalContext): Option[Term] =
    expressionFormTyping.typeNewObject(node, context)

  def typeStringInterpolation(node: AST.StringInterpolation, context: LocalContext): Option[Term] =
    expressionFormTyping.typeStringInterpolation(node, context)

  def typeBinaryAssignment(node: AST.Expression, lhs: AST.Expression, rhs: AST.Expression, binaryKind: Int, context: LocalContext): Option[Term] = {
    // Desugar: a += b becomes a = a + b
    // First, create the binary operation
    val binaryOp = binaryKind match {
      case ADD => new AST.Addition(node.location, lhs, rhs)
      case SUBTRACT => new AST.Subtraction(node.location, lhs, rhs)
      case MULTIPLY => new AST.Multiplication(node.location, lhs, rhs)
      case DIVIDE => new AST.Division(node.location, lhs, rhs)
      case MOD => new AST.Modulo(node.location, lhs, rhs)
    }

    // Then create the assignment: a = (a + b)
    val assignment = new AST.Assignment(node.location, lhs, binaryOp)

    // Type-check the assignment
    typeAssignment(assignment, context)
  }

  def typeNumericBinary(node: AST.BinaryExpression, kind: Int, context: LocalContext): Option[Term] =
    operatorTyping.typeNumericBinary(node, kind, context)

  def typeLogicalBinary(node: AST.BinaryExpression, kind: Int, context: LocalContext): Option[Term] =
    operatorTyping.typeLogicalBinary(node, kind, context)

  def typeComparableBinary(node: AST.BinaryExpression, kind: Int, context: LocalContext): Option[Term] =
    operatorTyping.typeComparableBinary(node, kind, context)

  def typeUnaryNumeric(node: AST.UnaryExpression, symbol: String, kind: Int, context: LocalContext): Option[Term] =
    operatorTyping.typeUnaryNumeric(node, symbol, kind, context)

  def typeUnaryBoolean(node: AST.UnaryExpression, symbol: String, kind: Int, context: LocalContext): Option[Term] =
    operatorTyping.typeUnaryBoolean(node, symbol, kind, context)

  def typePostUpdate(node: AST.Expression, termNode: AST.Expression, symbol: String, binaryKind: Int, context: LocalContext): Option[Term] =
    operatorTyping.typePostUpdate(node, termNode, symbol, binaryKind, context)

  def typeAssignment(node: AST.Assignment, context: LocalContext): Option[Term] =
    assignmentTyping.typeAssignment(node, context)

  def typeElvis(node: AST.Elvis, context: LocalContext): Option[Term] =
    expressionFormTyping.typeElvis(node, context)

  def typeCast(node: AST.Cast, context: LocalContext): Option[Term] =
    expressionFormTyping.typeCast(node, context)

  def typeIsInstance(node: AST.IsInstance, context: LocalContext): Option[Term] =
    expressionFormTyping.typeIsInstance(node, context)

  def typed(node: AST.Expression, context: LocalContext, expected: Type = null): Option[Term] = node match {
    case node@AST.Addition(loc, _, _) =>
      var left = typed(node.lhs, context).getOrElse(null)
      var right = typed(node.rhs, context).getOrElse(null)
      if (left == null || right == null) return None
      if (left.isBasicType && right.isBasicType) {
        return Option(processNumericExpression(ADD, node, left, right))
      }
      if (left.isBasicType) {
        if (left.`type` == BasicType.VOID) {
          report(IS_NOT_BOXABLE_TYPE, node.lhs, left.`type`)
          return None
        } else {
          left = Boxing.boxing(table_, left)
        }
      }
      if (right.isBasicType) {
        if (right.`type` == BasicType.VOID) {
          report(IS_NOT_BOXABLE_TYPE, node.rhs, right.`type`)
          return None
        }
        else {
          right = Boxing.boxing(table_, right)
        }
      }
      val toStringL = findMethod(node.lhs, left.`type`.asInstanceOf[ObjectType], "toString")
      val toStringR = findMethod(node.rhs, right.`type`.asInstanceOf[ObjectType], "toString")
      left = new Call(left, toStringL, new Array[Term](0))
      right = new Call(right, toStringR, new Array[Term](0))
      val concat: Method = findMethod(node, left.`type`.asInstanceOf[ObjectType], "concat", Array[Term](right))
      Some(new Call(left, concat, Array[Term](right)))
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
      Some(new ListLiteral(elements.map{e => typed(e, context).getOrElse(null)}.toArray, load("java.util.List")))
    case node@AST.NullLiteral(loc) =>
      Some(new NullValue(loc))
    case node: AST.Cast =>
      typeCast(node, context)
    case node: AST.ClosureExpression =>
      closureTyping.typeClosure(node, context, expected)
    case node@AST.CurrentInstance(loc) =>
      if(context.isStatic) None else Some(new This(loc, definition_))
    case node@AST.Id(loc, name) =>
      val bind = context.lookup(name)
      if (bind == null) {
        report(VARIABLE_NOT_FOUND, node, node.name)
        None
      }else {
        Some(new RefLocal(bind))
      }
    case node: AST.UnqualifiedFieldReference =>
      report(UNIMPLEMENTED_FEATURE, node)
      None
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
    // Removed: UnqualifiedFieldReference - use this.field or self.field instead
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
      report(UNIMPLEMENTED_FEATURE, node)
      None
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

  private def processNumericExpression(kind: Int, node: AST.BinaryExpression, lt: Term, rt: Term): Term =
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
