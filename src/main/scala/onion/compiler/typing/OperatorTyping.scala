package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.TypedAST.BinaryTerm.Kind as BinaryKind
import onion.compiler.TypedAST.BinaryTerm.Kind.*
import onion.compiler.TypedAST.UnaryTerm.Kind as UnaryKind
import onion.compiler.TypedAST.UnaryTerm.Kind.*
import onion.compiler.toolbox.Boxing
import onion.compiler.typing.session.TypingBodyContext

final class OperatorTyping(
  private val typing: Typing,
  private val bodyContext: TypingBodyContext,
  private val body: TypingBodyPass
) {

  def createEquals(kind: BinaryKind, lhs: Term, rhs: Term): Term = {
    lhs.`type` match {
      case target: ObjectType =>
        val params = Array[Term](new AsInstanceOf(rhs, bodyContext.rootClass))
        val methods = target.findMethod("equals", params)
        if (methods.isEmpty) {
          referenceEquals(kind, lhs, rhs)
        } else {
          var node: Term = new Call(lhs, methods(0), params)
          if (kind == NOT_EQUAL) {
            node = new UnaryTerm(NOT, BasicType.BOOLEAN, node)
          }
          node
        }
      case _ =>
        // No equals method to dispatch to (e.g. a null literal on the left):
        // fall back to reference comparison, which is the sensible semantics here.
        referenceEquals(kind, lhs, rhs)
    }
  }

  private def referenceEquals(kind: BinaryKind, lhs: Term, rhs: Term): Term =
    new BinaryTerm(kind, BasicType.BOOLEAN, lhs, rhs)

  def processEquals(kind: BinaryKind, node: AST.BinaryExpression, context: LocalContext): Term = {
    var left: Term = typed(node.lhs, context).getOrElse(null)
    var right: Term = typed(node.rhs, context).getOrElse(null)
    if (left == null || right == null) return null
    // Comparing a boxed wrapper against a primitive compares values: unbox the wrapper side
    if (left.isBasicType != right.isBasicType) {
      left = tryUnboxAny(left)
      right = tryUnboxAny(right)
    }
    val leftType: Type = left.`type`
    val rightType: Type = right.`type`
    if ((left.isBasicType && (!right.isBasicType)) || ((!left.isBasicType) && (right.isBasicType))) {
      bodyContext.report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](leftType, rightType))
      return null
    }
    if (left.isBasicType && right.isBasicType) {
      if (hasNumericType(left) && hasNumericType(right)) {
        val resultType = promote(leftType, rightType)
        if (resultType != left.`type`) left = new AsInstanceOf(left, resultType)
        if (resultType != right.`type`) right = new AsInstanceOf(right, resultType)
      } else if (leftType != BasicType.BOOLEAN || rightType != BasicType.BOOLEAN) {
        bodyContext.report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](leftType, rightType))
        return null
      }
    } else if (left.isReferenceType && right.isReferenceType) {
      return createEquals(kind, left, right)
    }
    new BinaryTerm(kind, BasicType.BOOLEAN, left, right)
  }

  def processShiftExpression(kind: BinaryKind, node: AST.BinaryExpression, context: LocalContext): Term = {
    var left: Term = typed(node.lhs, context).getOrElse(null)
    var right: Term = typed(node.rhs, context).getOrElse(null)
    if (left == null || right == null) return null
    // Unbox wrapper operands first; non-unboxable objects (e.g. List << elem) keep the method-call path
    left = Boxing.tryUnboxToInteger(bodyContext.table, left)
    right = Boxing.tryUnboxToInteger(bodyContext.table, right)
    if (!left.`type`.isBasicType) {
      left.`type` match {
        case target: ObjectType =>
          val params = Array[Term](right)
          tryFindMethod(node, target, "add", params) match {
            case Left(_) =>
              bodyContext.report(METHOD_NOT_FOUND, node, left.`type`, "add", types(params))
              return null
            case Right(method) =>
              return new Call(left, method, params)
          }
        case _ =>
          bodyContext.report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](left.`type`, right.`type`))
          return null
      }
    }
    if (!right.`type`.isBasicType) {
      bodyContext.report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](left.`type`, right.`type`))
      return null
    }
    val leftType: BasicType = left.`type`.asInstanceOf[BasicType]
    val rightType: BasicType = right.`type`.asInstanceOf[BasicType]
    if ((!leftType.isInteger) || (!rightType.isInteger)) {
      bodyContext.report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](left.`type`, right.`type`))
      return null
    }
    val leftResultType = promoteInteger(leftType)
    if (leftResultType != leftType) {
      left = new AsInstanceOf(left, leftResultType)
    }
    if (rightType != BasicType.INT) {
      right = new AsInstanceOf(right, BasicType.INT)
    }
    new BinaryTerm(kind, leftResultType, left, right)
  }

  def processComparableExpression(node: AST.BinaryExpression, context: LocalContext): Array[Term] = {
    val leftRaw = typed(node.lhs, context).getOrElse(null)
    val rightRaw = typed(node.rhs, context).getOrElse(null)
    if (leftRaw == null || rightRaw == null) return null

    // Try to unbox wrapper types to numeric primitives
    val left = Boxing.tryUnboxToNumeric(bodyContext.table, leftRaw, numericTypes.contains)
    val right = Boxing.tryUnboxToNumeric(bodyContext.table, rightRaw, numericTypes.contains)

    val leftType = left.`type`
    val rightType = right.`type`
    if ((!numeric(leftType)) || (!numeric(rightType))) {
      bodyContext.report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](leftType, rightType))
      null
    } else {
      val resultType = promote(leftType, rightType)
      val newLeft = if (leftType != resultType) new AsInstanceOf(left, resultType) else left
      val newRight = if (rightType != resultType) new AsInstanceOf(right, resultType) else right
      Array[Term](newLeft, newRight)
    }
  }

  def processBitExpression(kind: BinaryKind, node: AST.BinaryExpression, context: LocalContext): Term = {
    val leftRaw = typed(node.lhs, context).getOrElse(null)
    val rightRaw = typed(node.rhs, context).getOrElse(null)
    if (leftRaw == null || rightRaw == null) return null
    val left = tryUnboxAny(leftRaw)
    val right = tryUnboxAny(rightRaw)
    if ((!left.isBasicType) || (!right.isBasicType)) {
      bodyContext.report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](left.`type`, right.`type`))
      return null
    }
    val leftType = left.`type`.asInstanceOf[BasicType]
    val rightType = right.`type`.asInstanceOf[BasicType]
    var resultType: Type = null
    if (leftType.isInteger && rightType.isInteger) {
      resultType = promote(leftType, rightType)
    } else if (leftType.isBoolean && rightType.isBoolean) {
      resultType = BasicType.BOOLEAN
    } else {
      bodyContext.report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](leftType, rightType))
      return null
    }
    val newLeft = if (left.`type` != resultType) new AsInstanceOf(left, resultType) else left
    val newRight = if (right.`type` != resultType) new AsInstanceOf(right, resultType) else right
    new BinaryTerm(kind, resultType, newLeft, newRight)
  }

  def processLogicalExpression(node: AST.BinaryExpression, context: LocalContext): Array[Term] = {
    val leftRaw = typed(node.lhs, context).getOrElse(null)
    if (leftRaw == null) return null
    val left = Boxing.tryUnboxToBoolean(bodyContext.table, leftRaw)

    // For &&, apply smart cast narrowing from left side when typing right side
    val savedNarrowings = context.saveNarrowings()
    val isAnd = node.isInstanceOf[AST.LogicalAnd]
    if (isAnd) {
      val narrowing = body.extractNarrowing(node.lhs, context)
      narrowing.positive.foreach { case (name, tp) =>
        context.addNarrowing(name, tp)
      }
    }

    val rightRaw = typed(node.rhs, context).getOrElse(null)
    context.restoreNarrowings(savedNarrowings)

    if (rightRaw == null) return null
    val right = Boxing.tryUnboxToBoolean(bodyContext.table, rightRaw)
    val leftType: Type = left.`type`
    val rightType: Type = right.`type`
    if ((leftType != BasicType.BOOLEAN) || (rightType != BasicType.BOOLEAN)) {
      bodyContext.report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](left.`type`, right.`type`))
      null
    } else {
      Array[Term](left, right)
    }
  }

  def processRefEquals(kind: BinaryKind, node: AST.BinaryExpression, context: LocalContext): Term = {
    val leftRaw = typed(node.lhs, context).getOrElse(null)
    val rightRaw = typed(node.rhs, context).getOrElse(null)
    if (leftRaw == null || rightRaw == null) return null
    val leftType = leftRaw.`type`
    val rightType = rightRaw.`type`
    if ((leftRaw.isBasicType && (!rightRaw.isBasicType)) || ((!leftRaw.isBasicType) && (rightRaw.isBasicType))) {
      bodyContext.report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](leftType, rightType))
      return null
    }
    val (left, right) = if (leftRaw.isBasicType && rightRaw.isBasicType) {
      if (hasNumericType(leftRaw) && hasNumericType(rightRaw)) {
        val resultType: Type = promote(leftType, rightType)
        val newLeft = if (resultType != leftRaw.`type`) new AsInstanceOf(leftRaw, resultType) else leftRaw
        val newRight = if (resultType != rightRaw.`type`) new AsInstanceOf(rightRaw, resultType) else rightRaw
        (newLeft, newRight)
      } else if (leftType != BasicType.BOOLEAN || rightType != BasicType.BOOLEAN) {
        bodyContext.report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](leftType, rightType))
        return null
      } else {
        (leftRaw, rightRaw)
      }
    } else {
      (leftRaw, rightRaw)
    }
    new BinaryTerm(kind, BasicType.BOOLEAN, left, right)
  }

  def typeNumericBinary(node: AST.BinaryExpression, kind: BinaryKind, context: LocalContext): Option[Term] =
    for {
      leftRaw <- typed(node.lhs, context)
      rightRaw <- typed(node.rhs, context)
      left = Boxing.tryUnboxToNumeric(bodyContext.table, leftRaw, numericTypes.contains)
      right = Boxing.tryUnboxToNumeric(bodyContext.table, rightRaw, numericTypes.contains)
      result <- {
        // Operator overloading: when an operand is a user type rather than a
        // number, dispatch to the convention method (a - b => a.minus(b))
        if (!hasNumericType(left) || !hasNumericType(right))
          tryOperatorMethod(node, node.symbol, leftRaw, rightRaw)
            .orElse(Option(processNumericExpression(kind, node, left, right)))
        else
          Option(processNumericExpression(kind, node, left, right))
      }
    } yield result

  /**
   * Kotlin-style operator overloading: maps a binary operator to a
   * convention method on the left operand and builds the call when found.
   * Returns None when there is no mapping or no matching method, so callers
   * fall back to the primitive/concat path.
   */
  private val operatorMethodNames: Map[String, String] =
    Map("+" -> "plus", "-" -> "minus", "*" -> "times", "/" -> "div", "%" -> "rem")

  def tryOperatorMethod(node: AST.Node, symbol: String, left: Term, right: Term): Option[Term] = {
    val methodName = operatorMethodNames.getOrElse(symbol, return None)
    left.`type` match {
      case targetType: ObjectType if right.`type`.isObjectType || right.`type`.isBasicType =>
        val params = Array(right)
        tryFindMethod(node, targetType, methodName, params) match {
          case Right(method) =>
            val classSubst = TypeSubstitution.classSubstitution(targetType)
            val resultType = TypeSubstitution.substituteType(method.returnType, classSubst, scala.collection.immutable.Map.empty, defaultToBound = true)
            Some(TypeSubst.withCast(new Call(left, method, params), resultType))
          case Left(_) => None
        }
      case _ => None
    }
  }

  def typeLogicalBinary(node: AST.BinaryExpression, kind: BinaryKind, context: LocalContext): Option[Term] = {
    val ops = processLogicalExpression(node, context)
    if (ops == null) None else Some(new BinaryTerm(kind, BasicType.BOOLEAN, ops(0), ops(1)))
  }

  def typeComparableBinary(node: AST.BinaryExpression, kind: BinaryKind, context: LocalContext): Option[Term] = {
    val ops = processComparableExpression(node, context)
    if (ops == null) None else Some(new BinaryTerm(kind, BasicType.BOOLEAN, ops(0), ops(1)))
  }

  def typeUnaryNumeric(node: AST.UnaryExpression, symbol: String, kind: UnaryKind, context: LocalContext): Option[Term] =
    typed(node.term, context).flatMap { termRaw =>
      val term = Boxing.tryUnboxToNumeric(bodyContext.table, termRaw, numericTypes.contains)
      if (!hasNumericType(term)) {
        bodyContext.report(INCOMPATIBLE_OPERAND_TYPE, node, symbol, Array[Type](term.`type`))
        None
      } else Some(new UnaryTerm(kind, term.`type`, term))
    }

  /** ~x — integral operands only; byte/short/char promote to int like Java. */
  def typeUnaryIntegral(node: AST.UnaryExpression, symbol: String, kind: UnaryKind, context: LocalContext): Option[Term] =
    typed(node.term, context).flatMap { termRaw =>
      val term = Boxing.tryUnboxToNumeric(bodyContext.table, termRaw, numericTypes.contains)
      term.`type` match {
        case BasicType.INT | BasicType.LONG =>
          Some(new UnaryTerm(kind, term.`type`, term))
        case BasicType.BYTE | BasicType.SHORT | BasicType.CHAR =>
          Some(new UnaryTerm(kind, BasicType.INT, new AsInstanceOf(term, BasicType.INT)))
        case other =>
          bodyContext.report(INCOMPATIBLE_OPERAND_TYPE, node, symbol, Array[Type](other))
          None
      }
    }

  def typeUnaryBoolean(node: AST.UnaryExpression, symbol: String, kind: UnaryKind, context: LocalContext): Option[Term] =
    typed(node.term, context).flatMap { termRaw =>
      val term = Boxing.tryUnboxToBoolean(bodyContext.table, termRaw)
      if (term.`type` != BasicType.BOOLEAN) {
        bodyContext.report(INCOMPATIBLE_OPERAND_TYPE, node, symbol, Array[Type](term.`type`))
        None
      } else Some(new UnaryTerm(kind, BasicType.BOOLEAN, term))
    }

  def typePostUpdate(node: AST.Expression, termNode: AST.Expression, symbol: String, binaryKind: BinaryKind, context: LocalContext): Option[Term] = {
    val operand = typed(termNode, context).getOrElse(null)
    if (operand == null) return None
    if ((!operand.isBasicType) || !hasNumericType(operand)) {
      bodyContext.report(INCOMPATIBLE_OPERAND_TYPE, node, symbol, Array[Type](operand.`type`))
      return None
    }
    Option(operand match {
      case ref: RefLocal =>
        termNode match {
          case id: AST.Id =>
            val bind = context.lookup(id.name)
            if (bind != null && !bind.isMutable) {
              bodyContext.report(CANNOT_ASSIGN_TO_VAL, id, id.name)
              return None
            }
          case _ =>
        }
        val varIndex = context.add(context.newName, operand.`type`)
        new Begin(
          new SetLocal(0, varIndex, operand.`type`, operand),
          new SetLocal(ref.frame, ref.index, ref.`type`, new BinaryTerm(binaryKind, operand.`type`, new RefLocal(0, varIndex, operand.`type`), new IntValue(1))),
          new RefLocal(0, varIndex, operand.`type`)
        )
      case ref: RefField =>
        if (Modifier.isFinal(ref.field.modifier)) {
          bodyContext.report(CANNOT_ASSIGN_TO_VAL, termNode, ref.field.name)
          return None
        }
        val varIndex = context.add(context.newName, ref.target.`type`)
        new Begin(
          new SetLocal(0, varIndex, ref.target.`type`, ref.target),
          new SetField(
            new RefLocal(0, varIndex, ref.target.`type`),
            ref.field,
            new BinaryTerm(binaryKind, operand.`type`, new RefField(new RefLocal(0, varIndex, ref.target.`type`), ref.field), new IntValue(1))
          )
        )
      case _ =>
        bodyContext.report(LVALUE_REQUIRED, termNode)
        null
    })
  }

  def processNumericExpression(kind: BinaryKind, node: AST.BinaryExpression, lt: Term, rt: Term): Term = {
    if ((!hasNumericType(lt)) || (!hasNumericType(rt))) {
      bodyContext.report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](lt.`type`, rt.`type`))
      return null
    }
    val resultType = promote(lt.`type`, rt.`type`)
    val left = if (lt.`type` != resultType) new AsInstanceOf(lt, resultType) else lt
    val right = if (rt.`type` != resultType) new AsInstanceOf(rt, resultType) else rt
    new BinaryTerm(kind, resultType, left, right)
  }

  private def typed(node: AST.Expression, context: LocalContext, expected: Type = null): Option[Term] =
    body.typed(node, context, expected)

  private def tryFindMethod(node: AST.Node, target: ObjectType, name: String, params: Array[Term]): Either[Boolean, Method] =
    body.tryFindMethod(node, target, name, params)

  private def types(terms: Array[Term]): Array[Type] =
    body.types(terms)

  /** Try to unbox to a numeric or boolean primitive; returns the term unchanged if not a wrapper. */
  private def tryUnboxAny(term: Term): Term = {
    val numeric = Boxing.tryUnboxToNumeric(bodyContext.table, term, numericTypes.contains)
    if (numeric ne term) numeric
    else Boxing.tryUnboxToBoolean(bodyContext.table, term)
  }

  private def hasNumericType(term: Term): Boolean = numeric(term.`type`)

  private val numericTypes: Set[BasicType] = Set(
    BasicType.BYTE, BasicType.SHORT, BasicType.CHAR, BasicType.INT,
    BasicType.LONG, BasicType.FLOAT, BasicType.DOUBLE
  )

  private def numeric(symbol: Type): Boolean =
    symbol.isBasicType && numericTypes.contains(symbol.asInstanceOf[BasicType])

  private def promote(left: Type, right: Type): Type =
    if (!numeric(left) || !numeric(right)) null
    else if ((left eq BasicType.DOUBLE) || (right eq BasicType.DOUBLE)) BasicType.DOUBLE
    else if ((left eq BasicType.FLOAT) || (right eq BasicType.FLOAT)) BasicType.FLOAT
    else if ((left eq BasicType.LONG) || (right eq BasicType.LONG)) BasicType.LONG
    else BasicType.INT

  private def promoteInteger(typeRef: Type): Type = typeRef match {
    case BasicType.BYTE | BasicType.SHORT | BasicType.CHAR | BasicType.INT => BasicType.INT
    case BasicType.LONG => BasicType.LONG
    case _ => null
  }
}
