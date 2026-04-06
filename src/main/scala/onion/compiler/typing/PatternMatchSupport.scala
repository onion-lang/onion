package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.TypedAST.BinaryTerm.Kind.*

private[compiler] final class PatternMatchSupport(
  typing: Typing,
  typed: (AST.Expression, LocalContext) => Option[Term],
  createEqualsForRef: (Term, Term) => Term
) {
  import typing.*

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
      if (expressions(0).isReferenceType) createEqualsForRef(ref, expressions(0))
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
}
