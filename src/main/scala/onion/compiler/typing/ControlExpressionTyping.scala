package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.toolbox.Boxing
import onion.compiler.typing.session.TypingBodyContext

import scala.collection.mutable.Buffer
import scala.util.boundary, boundary.break

import TypeNarrowingAnalysis.NarrowingInfo

/**
 * Types control-flow expressions: blocks, if, loops and jump expressions.
 * `select` and `try`/`synchronized` are handled by [[SelectExpressionTyping]]
 * and [[TryExpressionTyping]]; shared branch-building helpers live here.
 */
final class ControlExpressionTyping(
  private val typing: Typing,
  private val bodyContext: TypingBodyContext,
  private val body: TypingBodyPass
) {

  private val selectTyping = new SelectExpressionTyping(typing, bodyContext, body, this)
  private val tryTyping = new TryExpressionTyping(typing, bodyContext, body, this)

  /**
   * Extracts type narrowing information from a condition expression.
   * Delegates to TypeNarrowingAnalysis with the type resolver.
   */
  private[typing] def extractNarrowing(condition: AST.Expression, context: LocalContext): NarrowingInfo =
    TypeNarrowingAnalysis.extractNarrowing(condition, context, typing.mapFrom, fieldNullNarrow)

  /** The non-null type of an immutable (`val`) nullable field of the current
    * class, for smart-casting an implicitly-accessed field after a null check.
    * Only final fields are eligible — they cannot change between check and use. */
  private def fieldNullNarrow(name: String): Option[TypedAST.Type] = {
    val field = bodyContext.definition.findField(name)
    if (field != null && Modifier.isFinal(field.modifier)) {
      field.`type` match {
        case n: TypedAST.NullableType => Some(n.innerType)
        case _ => None
      }
    } else None
  }

  def typeBlockExpression(node: AST.BlockExpression, context: LocalContext): Option[Term] =
    context.openScope {
      if (node.elements.isEmpty) {
        Some(statementTerm(new NOP(node.location), BasicType.VOID, node.location))
      } else {
        val terms = Buffer[Term]()
        var failed = false
        val lastIndex = node.elements.length - 1
        for (i <- node.elements.indices) {
          val element = node.elements(i)
          if (i < lastIndex) {
            val stmt = translate(element, context)
            terms += statementTerm(stmt, BasicType.VOID, element.location)
          } else {
            element match {
              case _: AST.LocalVariableDeclaration =>
                val stmt = translate(element, context)
                terms += statementTerm(stmt, BasicType.VOID, element.location)
              case expr: AST.Expression =>
                typed(expr, context) match {
                  case Some(term) => terms += term
                  case None => failed = true
                }
            }
          }
        }
        if (failed) None else Some(sequenceTerms(node.location, terms.toSeq))
      }
    }

  def typeIfExpression(node: AST.IfExpression, context: LocalContext): Option[Term] = boundary {
    context.openScope {
      val conditionRaw = typed(node.condition, context).getOrElse(break(None))
      val condition = ensureBoolean(node.condition, conditionRaw)
      if (condition == null) break(None)

      val narrowing = extractNarrowing(node.condition, context)
      val savedNarrowings = context.saveNarrowings()

      narrowing.positive.foreach { case (name, narrowedType) =>
        context.addNarrowing(name, narrowedType)
      }
      val thenTerm = typeBlockExpression(node.thenBlock, context).getOrElse {
        context.restoreNarrowings(savedNarrowings)
        break(None)
      }
      context.restoreNarrowings(savedNarrowings)

      if (node.elseBlock == null) {
        val thenStmt = termToStatement(node.thenBlock, thenTerm)
        Some(statementTerm(new IfStatement(condition, thenStmt, null), BasicType.VOID, node.location))
      } else {
        narrowing.negative.foreach { case (name, narrowedType) =>
          context.addNarrowing(name, narrowedType)
        }
        val elseTerm = typeBlockExpression(node.elseBlock, context).getOrElse {
          context.restoreNarrowings(savedNarrowings)
          break(None)
        }
        context.restoreNarrowings(savedNarrowings)

        val resultType = leastUpperBound(node, thenTerm.`type`, elseTerm.`type`)
        if (resultType == null) break(None)

        if (resultType.isBottomType || resultType == BasicType.VOID) {
          val thenStmt = termToStatement(node.thenBlock, thenTerm)
          val elseStmt = termToStatement(node.elseBlock, elseTerm)
          Some(statementTerm(new IfStatement(condition, thenStmt, elseStmt), resultType, node.location))
        } else {
          val resultVar = new ClosureLocalBinding(0, context.add(context.newName, resultType, isMutable = true), resultType, isMutable = true)
          val thenStmt = assignBranch(node.thenBlock, thenTerm, resultVar, resultType)
          val elseStmt = assignBranch(node.elseBlock, elseTerm, resultVar, resultType)
          Some(new Begin(node.location, Array[Term](statementTerm(new IfStatement(condition, thenStmt, elseStmt), BasicType.VOID, node.location), new RefLocal(resultVar))))
        }
      }
    }
  }

  def typeSelectExpression(node: AST.SelectExpression, context: LocalContext): Option[Term] =
    selectTyping.typeSelectExpression(node, context)

  def typeTryExpression(node: AST.TryExpression, context: LocalContext): Option[Term] =
    tryTyping.typeTryExpression(node, context)

  def typeSynchronizedExpression(node: AST.SynchronizedExpression, context: LocalContext): Option[Term] =
    tryTyping.typeSynchronizedExpression(node, context)

  def typeWhileExpression(node: AST.WhileExpression, context: LocalContext): Option[Term] =
    Some(statementTerm(translate(node, context), BasicType.VOID, node.location))

  def typeForExpression(node: AST.ForExpression, context: LocalContext): Option[Term] =
    Some(statementTerm(translate(node, context), BasicType.VOID, node.location))

  def typeForeachExpression(node: AST.ForeachExpression, context: LocalContext): Option[Term] =
    Some(statementTerm(translate(node, context), BasicType.VOID, node.location))

  def typeReturnExpression(node: AST.ReturnExpression, context: LocalContext): Option[Term] =
    Some(statementTerm(translate(node, context), BottomType.BOTTOM, node.location))

  def typeThrowExpression(node: AST.ThrowExpression, context: LocalContext): Option[Term] =
    Some(statementTerm(translate(node, context), BottomType.BOTTOM, node.location))

  def typeBreakExpression(node: AST.BreakExpression, context: LocalContext): Option[Term] =
    Some(statementTerm(translate(node, context), BottomType.BOTTOM, node.location))

  def typeContinueExpression(node: AST.ContinueExpression, context: LocalContext): Option[Term] =
    Some(statementTerm(translate(node, context), BottomType.BOTTOM, node.location))

  private def typed(node: AST.Expression, context: LocalContext, expected: Type = null): Option[Term] =
    body.typed(node, context, expected)

  private def translate(node: AST.BlockElement, context: LocalContext): ActionStatement =
    body.translate(node, context)

  private def processAssignable(node: AST.Node, expected: Type, term: Term): Term =
    body.processAssignable(node, expected, term)

  /** Ensures the term is boolean, unboxing if needed. Returns the term (possibly unboxed). */
  private[typing] def ensureBoolean(node: AST.Node, term: Term): Term =
    TypeCheckingHelpers.ensureBoolean(bodyContext.table, node, term,
      (n, actual) => bodyContext.report(INCOMPATIBLE_TYPE, n, BasicType.BOOLEAN, actual))

  private[typing] def termToStatement(node: AST.Node, term: Term): ActionStatement = term match {
    case stmtTerm: StatementTerm => stmtTerm.statement
    case _ => new ExpressionActionStatement(node.location, term)
  }

  private[typing] def assignBranch(node: AST.Node, term: Term, resultVar: ClosureLocalBinding, resultType: Type): ActionStatement =
    if (term.`type`.isBottomType) {
      termToStatement(node, term)
    } else {
      val assigned = processAssignable(node, resultType, term)
      if (assigned == null) new NOP(node.location)
      else new ExpressionActionStatement(new SetLocal(resultVar, assigned))
    }

  private def sequenceTerms(location: Location, terms: Seq[Term]): Term =
    terms match {
      case Seq() => statementTerm(new NOP(location), BasicType.VOID, location)
      case Seq(single) => single
      case _ => new Begin(location, terms.toArray)
    }

  private[typing] def statementTerm(statement: ActionStatement, termType: Type, location: Location): Term =
    new StatementTerm(location, statement, termType)

  private def leastUpperBound(node: AST.Node, left: Type, right: Type): Type =
    TypeCheckingHelpers.leastUpperBound(node, left, right, bodyContext.rootClass,
      (n, l, r) => bodyContext.report(INCOMPATIBLE_TYPE, n, l, r))

  private[typing] def foldLub(node: AST.Node, types: Seq[Type]): Option[Type] = boundary {
    types.reduceOption { (acc, t) =>
      val result = leastUpperBound(node, acc, t)
      if (result == null) break(None)
      result
    }
  }
}
