package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*

import scala.collection.mutable.Buffer

final class ControlExpressionTyping(private val typing: Typing, private val body: TypingBodyPass) {
  import typing.*

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
              case _: AST.LocalVariableDeclaration | _: AST.EmptyExpression =>
                val stmt = translate(element, context)
                terms += statementTerm(stmt, BasicType.VOID, element.location)
              case _ =>
                typed(element, context) match {
                  case Some(term) => terms += term
                  case None => failed = true
                }
            }
          }
        }
        if (failed) None else Some(sequenceTerms(node.location, terms.toSeq))
      }
    }

  def typeIfExpression(node: AST.IfExpression, context: LocalContext): Option[Term] =
    context.openScope {
      val conditionOpt = typed(node.condition, context)
      val condition = conditionOpt.getOrElse(null)
      if (condition == null) {
        None
      } else {
        ensureBoolean(node.condition, condition)

        val thenTermOpt = typeBlockExpression(node.thenBlock, context)
        val thenTerm = thenTermOpt.getOrElse(null)
        if (thenTerm == null) {
          None
        } else if (node.elseBlock == null) {
          val thenStmt = termToStatement(node.thenBlock, thenTerm)
          val stmt = new IfStatement(condition, thenStmt, null)
          Some(statementTerm(stmt, BasicType.VOID, node.location))
        } else {
          val elseTermOpt = typeBlockExpression(node.elseBlock, context)
          val elseTerm = elseTermOpt.getOrElse(null)
          if (elseTerm == null) {
            None
          } else {
            val resultType = leastUpperBound(node, thenTerm.`type`, elseTerm.`type`)
            if (resultType == null) {
              None
            } else if (resultType.isBottomType || resultType == BasicType.VOID) {
              val thenStmt = termToStatement(node.thenBlock, thenTerm)
              val elseStmt = termToStatement(node.elseBlock, elseTerm)
              val stmt = new IfStatement(condition, thenStmt, elseStmt)
              Some(statementTerm(stmt, resultType, node.location))
            } else {
              val resultVar = new ClosureLocalBinding(0, context.add(context.newName, resultType, isMutable = true), resultType, isMutable = true)
              val thenStmt = assignBranch(node.thenBlock, thenTerm, resultVar, resultType)
              val elseStmt = assignBranch(node.elseBlock, elseTerm, resultVar, resultType)
              val stmt = new IfStatement(condition, thenStmt, elseStmt)
              Some(new Begin(node.location, Array[Term](statementTerm(stmt, BasicType.VOID, node.location), new RefLocal(resultVar))))
            }
          }
        }
      }
    }

  def typeSelectExpression(node: AST.SelectExpression, context: LocalContext): Option[Term] = {
    val conditionOpt = typed(node.condition, context)
    val condition = conditionOpt.getOrElse(null)
    if (condition == null) return None

    val name = context.newName
    val index = context.add(name, condition.`type`, isMutable = true)
    val bind = context.lookup(name)

    val caseConditions = Buffer[Term]()
    val caseTerms = Buffer[Term]()
    val caseNodes = Buffer[AST.BlockExpression]()

    var failed = false
    val cases = node.cases.iterator
    while (cases.hasNext && !failed) {
      val (expressions, thenBlock) = cases.next()
      val cond = processNodes(expressions.toArray, condition.`type`, bind, context)
      if (cond == null) {
        failed = true
      } else {
        caseConditions += cond
        typeBlockExpression(thenBlock, context) match {
          case Some(term) =>
            caseTerms += term
            caseNodes += thenBlock
          case None => failed = true
        }
      }
    }
    if (failed) return None

    val elseTermOpt = Option(node.elseBlock).map(typeBlockExpression(_, context)).getOrElse(Some(statementTerm(new NOP(node.location), BasicType.VOID, node.location)))
    val elseTerm = elseTermOpt.getOrElse(null)
    if (elseTerm == null) return None

    val resultType =
      if (node.elseBlock == null) BasicType.VOID
      else {
        val branchTypes = caseTerms.map(_.`type`).toSeq :+ elseTerm.`type`
        foldLub(node, branchTypes).orNull
      }
    if (resultType == null) return None

    val resultVar =
      if (resultType.isBottomType || resultType == BasicType.VOID) null
      else new ClosureLocalBinding(0, context.add(context.newName, resultType, isMutable = true), resultType, isMutable = true)

    val caseStatements = caseTerms.indices.map { i =>
      val term = caseTerms(i)
      if (resultVar == null) termToStatement(caseNodes(i), term)
      else assignBranch(caseNodes(i), term, resultVar, resultType)
    }

    val elseStatement =
      if (node.elseBlock == null) null
      else if (resultVar == null) termToStatement(node.elseBlock, elseTerm)
      else assignBranch(node.elseBlock, elseTerm, resultVar, resultType)

    var branches: ActionStatement = elseStatement
    if (caseStatements.isEmpty && branches == null) {
      branches = new NOP(node.location)
    }
    for (i <- caseStatements.indices.reverse) {
      branches = new IfStatement(caseConditions(i), caseStatements(i), branches)
    }

    val init = new ExpressionActionStatement(new SetLocal(0, index, condition.`type`, condition))
    val statement = new StatementBlock(condition.location, init, branches)

    if (resultVar == null) {
      Some(statementTerm(statement, resultType, node.location))
    } else {
      Some(new Begin(node.location, Array[Term](statementTerm(statement, BasicType.VOID, node.location), new RefLocal(resultVar))))
    }
  }

  def typeTryExpression(node: AST.TryExpression, context: LocalContext): Option[Term] = {
    val tryTermOpt = typeBlockExpression(node.tryBlock, context)
    val tryTerm = tryTermOpt.getOrElse(null)
    if (tryTerm == null) return None

    val binds = new Array[ClosureLocalBinding](node.recClauses.length)
    val catchTerms = new Array[Term](node.recClauses.length)
    var failed = false
    for (i <- 0 until node.recClauses.length) {
      val (argument, body) = node.recClauses(i)
      context.openScope {
        val argType = addArgument(argument, context)
        val expected = load("java.lang.Throwable")
        if (!TypeRules.isSuperType(expected, argType)) {
          report(INCOMPATIBLE_TYPE, argument, expected, argType)
        }
        binds(i) = context.lookupOnlyCurrentScope(argument.name)
        typeBlockExpression(body, context) match {
          case Some(term) => catchTerms(i) = term
          case None => failed = true
        }
      }
    }
    if (failed) return None

    val resultType =
      if (catchTerms.isEmpty) tryTerm.`type`
      else {
        val types = tryTerm.`type` +: catchTerms.map(_.`type`).toSeq
        foldLub(node, types).orNull
      }
    if (resultType == null) return None

    val resultVar =
      if (resultType.isBottomType || resultType == BasicType.VOID) null
      else new ClosureLocalBinding(0, context.add(context.newName, resultType, isMutable = true), resultType, isMutable = true)

    val tryStmt =
      if (resultVar == null) termToStatement(node.tryBlock, tryTerm)
      else assignBranch(node.tryBlock, tryTerm, resultVar, resultType)

    val catchStatements = new Array[ActionStatement](catchTerms.length)
    for (i <- catchTerms.indices) {
      val term = catchTerms(i)
      val stmt =
        if (resultVar == null) termToStatement(node.recClauses(i)._2, term)
        else assignBranch(node.recClauses(i)._2, term, resultVar, resultType)
      catchStatements(i) = stmt
    }

    val statement = new Try(node.location, tryStmt, binds, catchStatements)
    if (resultVar == null) {
      Some(statementTerm(statement, resultType, node.location))
    } else {
      Some(new Begin(node.location, Array[Term](statementTerm(statement, BasicType.VOID, node.location), new RefLocal(resultVar))))
    }
  }

  def typeWhileExpression(node: AST.WhileExpression, context: LocalContext): Option[Term] =
    Some(statementTerm(translate(node, context), BasicType.VOID, node.location))

  def typeForExpression(node: AST.ForExpression, context: LocalContext): Option[Term] =
    Some(statementTerm(translate(node, context), BasicType.VOID, node.location))

  def typeForeachExpression(node: AST.ForeachExpression, context: LocalContext): Option[Term] =
    Some(statementTerm(translate(node, context), BasicType.VOID, node.location))

  def typeSynchronizedExpression(node: AST.SynchronizedExpression, context: LocalContext): Option[Term] =
    Some(statementTerm(translate(node, context), BasicType.VOID, node.location))

  def typeReturnExpression(node: AST.ReturnExpression, context: LocalContext): Option[Term] =
    Some(statementTerm(translate(node, context), BottomType.BOTTOM, node.location))

  def typeThrowExpression(node: AST.ThrowExpression, context: LocalContext): Option[Term] =
    Some(statementTerm(translate(node, context), BottomType.BOTTOM, node.location))

  def typeBreakExpression(node: AST.BreakExpression, context: LocalContext): Option[Term] =
    Some(statementTerm(translate(node, context), BottomType.BOTTOM, node.location))

  def typeContinueExpression(node: AST.ContinueExpression, context: LocalContext): Option[Term] =
    Some(statementTerm(translate(node, context), BottomType.BOTTOM, node.location))

  def typeExpressionBox(node: AST.ExpressionBox, context: LocalContext): Option[Term] =
    typed(node.body, context)

  def typeEmptyExpression(node: AST.EmptyExpression, context: LocalContext): Option[Term] =
    Some(statementTerm(new NOP(node.location), BasicType.VOID, node.location))

  private def typed(node: AST.Expression, context: LocalContext, expected: Type = null): Option[Term] =
    body.typed(node, context, expected)

  private def translate(node: AST.CompoundExpression, context: LocalContext): ActionStatement =
    body.translate(node, context)

  private def processAssignable(node: AST.Node, expected: Type, term: Term): Term =
    body.processAssignable(node, expected, term)

  private def processNodes(nodes: Array[AST.Expression], typeRef: Type, bind: ClosureLocalBinding, context: LocalContext): Term =
    body.processNodes(nodes, typeRef, bind, context)

  private def addArgument(arg: AST.Argument, context: LocalContext): Type =
    body.addArgument(arg, context)

  private def ensureBoolean(node: AST.Node, term: Term): Unit =
    if (term != null && term.`type` != BasicType.BOOLEAN) {
      report(INCOMPATIBLE_TYPE, node, BasicType.BOOLEAN, term.`type`)
    }

  private def termToStatement(node: AST.Node, term: Term): ActionStatement = term match {
    case st: StatementTerm => st.statement
    case _ => new ExpressionActionStatement(node.location, term)
  }

  private def assignBranch(node: AST.Node, term: Term, resultVar: ClosureLocalBinding, resultType: Type): ActionStatement =
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

  private def statementTerm(statement: ActionStatement, termType: Type, location: Location): Term =
    new StatementTerm(location, statement, termType)

  private def leastUpperBound(node: AST.Node, left: Type, right: Type): Type = {
    if (left == null || right == null) return null
    if (left.isBottomType) return right
    if (right.isBottomType) return left
    if (left eq right) return left
    if (left.isNullType && right.isNullType) return left
    if ((left eq BasicType.VOID) || (right eq BasicType.VOID)) {
      if ((left eq BasicType.VOID) && (right eq BasicType.VOID)) return BasicType.VOID
      report(INCOMPATIBLE_TYPE, node, left, right)
      return null
    }
    if (TypeRules.isSuperType(left, right)) return left
    if (TypeRules.isSuperType(right, left)) return right
    if (!left.isBasicType && !right.isBasicType) return rootClass
    report(INCOMPATIBLE_TYPE, node, left, right)
    null
  }

  private def foldLub(node: AST.Node, types: Seq[Type]): Option[Type] =
    if (types.isEmpty) {
      None
    } else {
      val it = types.iterator
      var acc = it.next()
      while (it.hasNext) {
        acc = leastUpperBound(node, acc, it.next())
        if (acc == null) return None
      }
      Some(acc)
    }
}
