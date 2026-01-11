package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.TypedAST.BinaryTerm.Kind as BinaryKind
import onion.compiler.TypedAST.BinaryTerm.Kind.*
import onion.compiler.TypedAST.UnaryTerm.Kind as UnaryKind
import onion.compiler.TypedAST.UnaryTerm.Kind.*
import onion.compiler.toolbox.Boxing

final class OperatorTyping(private val typing: Typing, private val body: TypingBodyPass) {
  import typing.*

  def createEquals(kind: BinaryKind, lhs: Term, rhs: Term): Term = {
    val params = Array[Term](new AsInstanceOf(rhs, rootClass))
    val target = lhs.`type`.asInstanceOf[ObjectType]
    val methods = target.findMethod("equals", params)
    var node: Term = new Call(lhs, methods(0), params)
    if (kind == NOT_EQUAL) {
      node = new UnaryTerm(NOT, BasicType.BOOLEAN, node)
    }
    node
  }

  def processEquals(kind: BinaryKind, node: AST.BinaryExpression, context: LocalContext): Term = {
    var left: Term = typed(node.lhs, context).getOrElse(null)
    var right: Term = typed(node.rhs, context).getOrElse(null)
    if (left == null || right == null) return null
    val leftType: Type = left.`type`
    val rightType: Type = right.`type`
    if ((left.isBasicType && (!right.isBasicType)) || ((!left.isBasicType) && (right.isBasicType))) {
      report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](leftType, rightType))
      return null
    }
    if (left.isBasicType && right.isBasicType) {
      if (hasNumericType(left) && hasNumericType(right)) {
        val resultType = promote(leftType, rightType)
        if (resultType != left.`type`) left = new AsInstanceOf(left, resultType)
        if (resultType != right.`type`) right = new AsInstanceOf(right, resultType)
      } else if (leftType != BasicType.BOOLEAN || rightType != BasicType.BOOLEAN) {
        report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](leftType, rightType))
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
    if (!left.`type`.isBasicType) {
      val params = Array[Term](right)
      tryFindMethod(node, left.`type`.asInstanceOf[ObjectType], "add", params) match {
        case Left(_) =>
          report(METHOD_NOT_FOUND, node, left.`type`, "add", types(params))
          return null
        case Right(method) =>
          return new Call(left, method, params)
      }
    }
    if (!right.`type`.isBasicType) {
      report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](left.`type`, right.`type`))
      return null
    }
    val leftType: BasicType = left.`type`.asInstanceOf[BasicType]
    val rightType: BasicType = right.`type`.asInstanceOf[BasicType]
    if ((!leftType.isInteger) || (!rightType.isInteger)) {
      report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](left.`type`, right.`type`))
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
    val left = Boxing.tryUnboxToNumeric(table_, leftRaw, numericTypes.contains)
    val right = Boxing.tryUnboxToNumeric(table_, rightRaw, numericTypes.contains)

    val leftType = left.`type`
    val rightType = right.`type`
    if ((!numeric(leftType)) || (!numeric(rightType))) {
      report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](leftType, rightType))
      null
    } else {
      val resultType = promote(leftType, rightType)
      val newLeft = if (leftType != resultType) new AsInstanceOf(left, resultType) else left
      val newRight = if (rightType != resultType) new AsInstanceOf(right, resultType) else right
      Array[Term](newLeft, newRight)
    }
  }

  def processBitExpression(kind: BinaryKind, node: AST.BinaryExpression, context: LocalContext): Term = {
    val left = typed(node.lhs, context).getOrElse(null)
    val right = typed(node.rhs, context).getOrElse(null)
    if (left == null || right == null) return null
    if ((!left.isBasicType) || (!right.isBasicType)) {
      report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](left.`type`, right.`type`))
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
      report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](leftType, rightType))
      return null
    }
    val newLeft = if (left.`type` != resultType) new AsInstanceOf(left, resultType) else left
    val newRight = if (right.`type` != resultType) new AsInstanceOf(right, resultType) else right
    new BinaryTerm(kind, resultType, newLeft, newRight)
  }

  def processLogicalExpression(node: AST.BinaryExpression, context: LocalContext): Array[Term] = {
    val left = typed(node.lhs, context).getOrElse(null)
    val right = typed(node.rhs, context).getOrElse(null)
    if (left == null || right == null) return null
    val leftType: Type = left.`type`
    val rightType: Type = right.`type`
    if ((leftType != BasicType.BOOLEAN) || (rightType != BasicType.BOOLEAN)) {
      report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](left.`type`, right.`type`))
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
      report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](leftType, rightType))
      return null
    }
    val (left, right) = if (leftRaw.isBasicType && rightRaw.isBasicType) {
      if (hasNumericType(leftRaw) && hasNumericType(rightRaw)) {
        val resultType: Type = promote(leftType, rightType)
        val newLeft = if (resultType != leftRaw.`type`) new AsInstanceOf(leftRaw, resultType) else leftRaw
        val newRight = if (resultType != rightRaw.`type`) new AsInstanceOf(rightRaw, resultType) else rightRaw
        (newLeft, newRight)
      } else if (leftType != BasicType.BOOLEAN || rightType != BasicType.BOOLEAN) {
        report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](leftType, rightType))
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
      left = Boxing.tryUnboxToNumeric(table_, leftRaw, numericTypes.contains)
      right = Boxing.tryUnboxToNumeric(table_, rightRaw, numericTypes.contains)
      result <- Option(processNumericExpression(kind, node, left, right))
    } yield result

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
      val term = Boxing.tryUnboxToNumeric(table_, termRaw, numericTypes.contains)
      if (!hasNumericType(term)) {
        report(INCOMPATIBLE_OPERAND_TYPE, node, symbol, Array[Type](term.`type`))
        None
      } else Some(new UnaryTerm(kind, term.`type`, term))
    }

  def typeUnaryBoolean(node: AST.UnaryExpression, symbol: String, kind: UnaryKind, context: LocalContext): Option[Term] =
    typed(node.term, context).flatMap { termRaw =>
      val term = Boxing.tryUnboxToBoolean(table_, termRaw)
      if (term.`type` != BasicType.BOOLEAN) {
        report(INCOMPATIBLE_OPERAND_TYPE, node, symbol, Array[Type](term.`type`))
        None
      } else Some(new UnaryTerm(kind, BasicType.BOOLEAN, term))
    }

  def typePostUpdate(node: AST.Expression, termNode: AST.Expression, symbol: String, binaryKind: BinaryKind, context: LocalContext): Option[Term] = {
    val operand = typed(termNode, context).getOrElse(null)
    if (operand == null) return None
    if ((!operand.isBasicType) || !hasNumericType(operand)) {
      report(INCOMPATIBLE_OPERAND_TYPE, node, symbol, Array[Type](operand.`type`))
      return None
    }
    Option(operand match {
      case ref: RefLocal =>
        termNode match {
          case id: AST.Id =>
            val bind = context.lookup(id.name)
            if (bind != null && !bind.isMutable) {
              report(CANNOT_ASSIGN_TO_VAL, id, id.name)
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
          report(CANNOT_ASSIGN_TO_VAL, termNode, ref.field.name)
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
        report(LVALUE_REQUIRED, termNode)
        null
    })
  }

  def processNumericExpression(kind: BinaryKind, node: AST.BinaryExpression, lt: Term, rt: Term): Term = {
    if ((!hasNumericType(lt)) || (!hasNumericType(rt))) {
      report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](lt.`type`, rt.`type`))
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
