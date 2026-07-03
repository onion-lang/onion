package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*
import onion.compiler.TypedAST.BinaryTerm.Kind.*
import onion.compiler.TypedAST.UnaryTerm.Kind.*

private[compiler] final class ExpressionDispatchSupport(body: TypingBodyPass) {
  def typed(node: AST.Expression, context: LocalContext, expected: Type = null): Option[Term] =
    node match {
      case node: AST.Addition =>
        body.typeAdditionNode(node, context)
      case node@AST.Subtraction(_, _, _) =>
        body.typeNumericBinary(node, SUBTRACT, context)
      case node@AST.Multiplication(_, _, _) =>
        body.typeNumericBinary(node, MULTIPLY, context)
      case node@AST.Division(_, _, _) =>
        body.typeNumericBinary(node, DIVIDE, context)
      case node@AST.Modulo(_, _, _) =>
        body.typeNumericBinary(node, MOD, context)
      case node: AST.Assignment =>
        body.typeAssignment(node, context)
      case node@AST.LogicalAnd(_, _, _) =>
        body.typeLogicalBinary(node, LOGICAL_AND, context)
      case node@AST.LogicalOr(_, _, _) =>
        body.typeLogicalBinary(node, LOGICAL_OR, context)
      case node@AST.BitAnd(_, _, _) =>
        Option(body.processBitExpression(BIT_AND, node, context))
      case node@AST.BitOr(_, _, _) =>
        Option(body.processBitExpression(BIT_OR, node, context))
      case node@AST.XOR(_, _, _) =>
        Option(body.processBitExpression(XOR, node, context))
      case node@AST.LogicalRightShift(_, _, _) =>
        Option(body.processShiftExpression(BIT_SHIFT_R3, node, context))
      case node@AST.MathLeftShift(_, _, _) =>
        Option(body.processShiftExpression(BIT_SHIFT_L2, node, context))
      case node@AST.MathRightShift(_, _, _) =>
        Option(body.processShiftExpression(BIT_SHIFT_R2, node, context))
      case node@AST.GreaterOrEqual(_, _, _) =>
        body.typeComparableBinary(node, GREATER_OR_EQUAL, context)
      case node@AST.GreaterThan(_, _, _) =>
        body.typeComparableBinary(node, GREATER_THAN, context)
      case node@AST.LessOrEqual(_, _, _) =>
        body.typeComparableBinary(node, LESS_OR_EQUAL, context)
      case node@AST.LessThan(_, _, _) =>
        body.typeComparableBinary(node, LESS_THAN, context)
      case node@AST.Equal(_, _, _) =>
        Option(body.processEquals(EQUAL, node, context))
      case node@AST.NotEqual(_, _, _) =>
        Option(body.processEquals(NOT_EQUAL, node, context))
      case node@AST.ReferenceEqual(_, _, _) =>
        Option(body.processRefEquals(EQUAL, node, context))
      case node@AST.ReferenceNotEqual(_, _, _) =>
        Option(body.processRefEquals(NOT_EQUAL, node, context))
      case node: AST.Elvis =>
        body.typeElvis(node, context)
      case node: AST.Indexing =>
        body.typeIndexing(node, context)
      case node: AST.SafeIndexing =>
        body.typeSafeIndexing(node, context)
      case node: AST.NotNullAssertion =>
        body.typeNotNullAssertion(node, context)
      case node: AST.Cast =>
        body.typeCast(node, context)
      case node: AST.ClosureExpression =>
        body.typeClosureNode(node, context, expected)
      case node: AST.IsInstance =>
        body.typeIsInstance(node, context)
      case node: AST.MemberSelection =>
        body.typeMemberSelection(node, context)
      case node: AST.MethodCall =>
        body.typeMethodCall(node, context, expected)
      case node: AST.SafeMemberSelection =>
        body.typeSafeMemberSelection(node, context)
      case node: AST.SafeMethodCall =>
        body.typeSafeMethodCall(node, context, expected)
      case node@AST.Negate(_, _) =>
        body.typeUnaryNumeric(node, "-", MINUS, context)
      case node: AST.NewArray =>
        body.typeNewArray(node, context)
      case node: AST.NewArrayWithValues =>
        body.typeNewArrayWithValues(node, context)
      case node: AST.NewObject =>
        body.typeNewObject(node, context, expected)
      case node@AST.Not(_, _) =>
        body.typeUnaryBoolean(node, "!", NOT, context)
      case node@AST.BitNot(_, _) =>
        body.typeUnaryIntegral(node, "~", BIT_NOT, context)
      case node@AST.Posit(_, _) =>
        body.typeUnaryNumeric(node, "+", PLUS, context)
      case node@AST.PostDecrement(_, _) =>
        body.typePostUpdate(node, node.term, "--", SUBTRACT, context)
      case node@AST.PostIncrement(_, _) =>
        body.typePostUpdate(node, node.term, "++", ADD, context)
      case node: AST.UnqualifiedMethodCall =>
        body.typeUnqualifiedMethodCall(node, context, expected)
      case node: AST.StaticMemberSelection =>
        body.typeStaticMemberSelection(node)
      case node: AST.StaticMethodCall =>
        body.typeStaticMethodCall(node, context, expected)
      case node: AST.StringInterpolation =>
        body.typeStringInterpolation(node, context)
      case node: AST.SuperMethodCall =>
        body.typeSuperMethodCall(node, context, expected)
      case node: AST.BlockExpression =>
        body.controlExpressionTyping.typeBlockExpression(node, context)
      case node: AST.BreakExpression =>
        body.controlExpressionTyping.typeBreakExpression(node, context)
      case node: AST.ContinueExpression =>
        body.controlExpressionTyping.typeContinueExpression(node, context)
      case node: AST.ForeachExpression =>
        body.controlExpressionTyping.typeForeachExpression(node, context)
      case node: AST.ForExpression =>
        body.controlExpressionTyping.typeForExpression(node, context)
      case node: AST.IfExpression =>
        body.controlExpressionTyping.typeIfExpression(node, context, expected)
      case node: AST.ReturnExpression =>
        body.controlExpressionTyping.typeReturnExpression(node, context)
      case node: AST.SelectExpression =>
        body.controlExpressionTyping.typeSelectExpression(node, context)
      case node: AST.SynchronizedExpression =>
        body.controlExpressionTyping.typeSynchronizedExpression(node, context)
      case node: AST.ThrowExpression =>
        body.controlExpressionTyping.typeThrowExpression(node, context)
      case node: AST.TryExpression =>
        body.controlExpressionTyping.typeTryExpression(node, context)
      case node: AST.WhileExpression =>
        body.controlExpressionTyping.typeWhileExpression(node, context)
      case _: AST.RetStatement | _: AST.DoExpression =>
        None
      case node =>
        body.typeSimpleExpression(node, context, expected)
    }
}
