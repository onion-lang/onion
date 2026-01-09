package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.TypedAST.BinaryTerm.Constants.*
import onion.compiler.TypedAST.UnaryTerm.Constants.*
import onion.compiler.toolbox.Boxing

final class OperatorTyping(private val typing: Typing, private val body: TypingBodyPass) {
  import typing.*

  def createEquals(kind: Int, lhs: Term, rhs: Term): Term = {
    val params = Array[Term](new AsInstanceOf(rhs, rootClass))
    val target = lhs.`type`.asInstanceOf[ObjectType]
    val methods = target.findMethod("equals", params)
    var node: Term = new Call(lhs, methods(0), params)
    if (kind == NOT_EQUAL) {
      node = new UnaryTerm(NOT, BasicType.BOOLEAN, node)
    }
    node
  }

  def processEquals(kind: Int, node: AST.BinaryExpression, context: LocalContext): Term = {
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

  def processShiftExpression(kind: Int, node: AST.BinaryExpression, context: LocalContext): Term = {
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
    var left = typed(node.lhs, context).getOrElse(null)
    var right = typed(node.rhs, context).getOrElse(null)
    if (left == null || right == null) return null

    // Try to unbox wrapper types to numeric primitives
    if (!left.isBasicType) {
      Boxing.unboxedType(table_, left.`type`).filter(numeric) match {
        case Some(bt) => left = Boxing.unboxing(table_, left, bt)
        case None => // will fail below
      }
    }
    if (!right.isBasicType) {
      Boxing.unboxedType(table_, right.`type`).filter(numeric) match {
        case Some(bt) => right = Boxing.unboxing(table_, right, bt)
        case None => // will fail below
      }
    }

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

  def processBitExpression(kind: Int, node: AST.BinaryExpression, context: LocalContext): Term = {
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

  def processRefEquals(kind: Int, node: AST.BinaryExpression, context: LocalContext): Term = {
    var left = typed(node.lhs, context).getOrElse(null)
    var right = typed(node.rhs, context).getOrElse(null)
    if (left == null || right == null) return null
    val leftType = left.`type`
    val rightType = right.`type`
    if ((left.isBasicType && (!right.isBasicType)) || ((!left.isBasicType) && (right.isBasicType))) {
      report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](leftType, rightType))
      return null
    }
    if (left.isBasicType && right.isBasicType) {
      if (hasNumericType(left) && hasNumericType(right)) {
        val resultType: Type = promote(leftType, rightType)
        if (resultType != left.`type`) {
          left = new AsInstanceOf(left, resultType)
        }
        if (resultType != right.`type`) {
          right = new AsInstanceOf(right, resultType)
        }
      } else if (leftType != BasicType.BOOLEAN || rightType != BasicType.BOOLEAN) {
        report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](leftType, rightType))
        return null
      }
    }
    new BinaryTerm(kind, BasicType.BOOLEAN, left, right)
  }

  def typeNumericBinary(node: AST.BinaryExpression, kind: Int, context: LocalContext): Option[Term] = {
    var left = typed(node.lhs, context).getOrElse(null)
    var right = typed(node.rhs, context).getOrElse(null)
    if (left == null || right == null) return None

    // Try to unbox wrapper types to numeric primitives
    if (!left.isBasicType) {
      Boxing.unboxedType(table_, left.`type`).filter(numeric) match {
        case Some(bt) => left = Boxing.unboxing(table_, left, bt)
        case None => // will fail in processNumericExpression
      }
    }
    if (!right.isBasicType) {
      Boxing.unboxedType(table_, right.`type`).filter(numeric) match {
        case Some(bt) => right = Boxing.unboxing(table_, right, bt)
        case None => // will fail in processNumericExpression
      }
    }

    Option(processNumericExpression(kind, node, left, right))
  }

  def typeLogicalBinary(node: AST.BinaryExpression, kind: Int, context: LocalContext): Option[Term] = {
    val ops = processLogicalExpression(node, context)
    if (ops == null) None else Some(new BinaryTerm(kind, BasicType.BOOLEAN, ops(0), ops(1)))
  }

  def typeComparableBinary(node: AST.BinaryExpression, kind: Int, context: LocalContext): Option[Term] = {
    val ops = processComparableExpression(node, context)
    if (ops == null) None else Some(new BinaryTerm(kind, BasicType.BOOLEAN, ops(0), ops(1)))
  }

  def typeUnaryNumeric(node: AST.UnaryExpression, symbol: String, kind: Int, context: LocalContext): Option[Term] = {
    var term = typed(node.term, context).getOrElse(null)
    if (term == null) return None

    // Try to unbox wrapper types to numeric primitives
    if (!term.isBasicType) {
      Boxing.unboxedType(table_, term.`type`).filter(numeric) match {
        case Some(bt) => term = Boxing.unboxing(table_, term, bt)
        case None => // will fail below
      }
    }

    if (!hasNumericType(term)) {
      report(INCOMPATIBLE_OPERAND_TYPE, node, symbol, Array[Type](term.`type`))
      None
    } else {
      Some(new UnaryTerm(kind, term.`type`, term))
    }
  }

  def typeUnaryBoolean(node: AST.UnaryExpression, symbol: String, kind: Int, context: LocalContext): Option[Term] = {
    var term = typed(node.term, context).getOrElse(null)
    if (term == null) return None

    // Try to unbox Boolean wrapper type
    if (!term.isBasicType) {
      Boxing.unboxedType(table_, term.`type`) match {
        case Some(BasicType.BOOLEAN) => term = Boxing.unboxing(table_, term, BasicType.BOOLEAN)
        case _ => // will fail below
      }
    }

    if (term.`type` != BasicType.BOOLEAN) {
      report(INCOMPATIBLE_OPERAND_TYPE, node, symbol, Array[Type](term.`type`))
      None
    } else {
      Some(new UnaryTerm(kind, BasicType.BOOLEAN, term))
    }
  }

  def typePostUpdate(node: AST.Expression, termNode: AST.Expression, symbol: String, binaryKind: Int, context: LocalContext): Option[Term] = {
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

  def processNumericExpression(kind: Int, node: AST.BinaryExpression, lt: Term, rt: Term): Term = {
    var left = lt
    var right = rt
    if ((!hasNumericType(left)) || (!hasNumericType(right))) {
      report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](left.`type`, right.`type`))
      return null
    }
    val resultType = promote(left.`type`, right.`type`)
    if (left.`type` != resultType) left = new AsInstanceOf(left, resultType)
    if (right.`type` != resultType) right = new AsInstanceOf(right, resultType)
    new BinaryTerm(kind, resultType, left, right)
  }

  private def typed(node: AST.Expression, context: LocalContext, expected: Type = null): Option[Term] =
    body.typed(node, context, expected)

  private def tryFindMethod(node: AST.Node, target: ObjectType, name: String, params: Array[Term]): Either[Boolean, Method] =
    body.tryFindMethod(node, target, name, params)

  private def types(terms: Array[Term]): Array[Type] =
    body.types(terms)

  private def hasNumericType(term: Term): Boolean = numeric(term.`type`)

  private def numeric(symbol: Type): Boolean =
    symbol.isBasicType && (symbol == BasicType.BYTE || symbol == BasicType.SHORT || symbol == BasicType.CHAR || symbol == BasicType.INT || symbol == BasicType.LONG || symbol == BasicType.FLOAT || symbol == BasicType.DOUBLE)

  private def promote(left: Type, right: Type): Type = {
    if (!numeric(left) || !numeric(right)) return null
    if ((left eq BasicType.DOUBLE) || (right eq BasicType.DOUBLE)) {
      return BasicType.DOUBLE
    }
    if ((left eq BasicType.FLOAT) || (right eq BasicType.FLOAT)) {
      return BasicType.FLOAT
    }
    if ((left eq BasicType.LONG) || (right eq BasicType.LONG)) {
      return BasicType.LONG
    }
    BasicType.INT
  }

  private def promoteInteger(typeRef: Type): Type = {
    if (typeRef == BasicType.BYTE || typeRef == BasicType.SHORT || typeRef == BasicType.CHAR || typeRef == BasicType.INT) {
      return BasicType.INT
    }
    if (typeRef == BasicType.LONG) {
      return BasicType.LONG
    }
    null
  }
}
