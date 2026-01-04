package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.toolbox.Boxing

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
      val condition = ensureBoolean(node.condition, conditionOpt.getOrElse(null))
      if (condition == null) {
        None
      } else {

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
    val caseBindings = Buffer[Option[ClosureLocalBinding]]()
    val matchedTypes = Buffer[Type]() // Track matched types for exhaustiveness check

    var failed = false
    val cases = node.cases.iterator
    while (cases.hasNext && !failed) {
      val (patterns, thenBlock) = cases.next()
      val (cond, typePatternInfo) = processPatterns(patterns.toArray, condition.`type`, bind, context)
      if (cond == null) {
        failed = true
      } else {
        caseConditions += cond

        // For type patterns, add the bound variable to context before typing the block
        typePatternInfo match {
          case Some((varName, varType)) =>
            matchedTypes += varType // Track for exhaustiveness
            context.openScope {
              val varIndex = context.add(varName, varType, isMutable = false)
              val varBind = new ClosureLocalBinding(0, varIndex, varType, isMutable = false, isBoxed = false)
              caseBindings += Some(varBind)
              typeBlockExpression(thenBlock, context) match {
                case Some(term) =>
                  caseTerms += term
                  caseNodes += thenBlock
                case None => failed = true
              }
            }
          case None =>
            caseBindings += None
            typeBlockExpression(thenBlock, context) match {
              case Some(term) =>
                caseTerms += term
                caseNodes += thenBlock
              case None => failed = true
            }
        }
      }
    }
    if (failed) return None

    // Exhaustiveness check for sealed types
    var isExhaustive = false
    if (node.elseBlock == null && matchedTypes.nonEmpty) {
      condition.`type` match {
        case classDef: ClassDefinition if classDef.isSealed =>
          val sealedSubtypes = classDef.sealedSubtypes
          val missingTypes = sealedSubtypes.filterNot { subtype =>
            matchedTypes.exists { matchedType =>
              matchedType match {
                case mt: ClassType => mt.name == subtype.name
                case _ => false
              }
            }
          }
          if (missingTypes.nonEmpty) {
            report(NON_EXHAUSTIVE_PATTERN_MATCH, node, condition.`type`, missingTypes.map(_.asInstanceOf[Type]))
          } else {
            isExhaustive = true
          }
        case _ =>
      }
    }

    val elseTermOpt = Option(node.elseBlock).map(typeBlockExpression(_, context)).getOrElse(Some(statementTerm(new NOP(node.location), BasicType.VOID, node.location)))
    val elseTerm = elseTermOpt.getOrElse(null)
    if (elseTerm == null) return None

    val resultType =
      if (node.elseBlock == null && !isExhaustive) BasicType.VOID
      else if (node.elseBlock == null && isExhaustive) {
        // Exhaustive pattern match: use LUB of all case branches
        val branchTypes = caseTerms.map(_.`type`).toSeq
        foldLub(node, branchTypes).orNull
      } else {
        val branchTypes = caseTerms.map(_.`type`).toSeq :+ elseTerm.`type`
        foldLub(node, branchTypes).orNull
      }
    if (resultType == null) return None

    val resultVar =
      if (resultType.isBottomType || resultType == BasicType.VOID) null
      else new ClosureLocalBinding(0, context.add(context.newName, resultType, isMutable = true), resultType, isMutable = true)

    val caseStatements = caseTerms.indices.map { i =>
      val term = caseTerms(i)
      val baseStmt = if (resultVar == null) termToStatement(caseNodes(i), term)
                     else assignBranch(caseNodes(i), term, resultVar, resultType)

      // For type patterns, wrap the statement with variable binding
      caseBindings(i) match {
        case Some(varBind) =>
          val cast = new AsInstanceOf(new RefLocal(bind), varBind.tp)
          val setVar = new ExpressionActionStatement(new SetLocal(varBind, cast))
          new StatementBlock(caseNodes(i).location, setVar, baseStmt)
        case None => baseStmt
      }
    }

    val elseStatement =
      if (node.elseBlock == null && isExhaustive && resultVar != null) {
        // For exhaustive patterns, add unreachable fallback to satisfy JVM verifier
        // Set resultVar to a default value (this code should never execute)
        new ExpressionActionStatement(new SetLocal(resultVar, body.defaultValue(resultType)))
      } else if (node.elseBlock == null) {
        null
      } else if (resultVar == null) {
        termToStatement(node.elseBlock, elseTerm)
      } else {
        assignBranch(node.elseBlock, elseTerm, resultVar, resultType)
      }

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

  /** Returns (condition, optional type pattern info: (name, type)) */
  private def processPatterns(patterns: Array[AST.Pattern], conditionType: Type, bind: ClosureLocalBinding, context: LocalContext): (Term, Option[(String, Type)]) = {
    var typePatternInfo: Option[(String, Type)] = None

    // Process each pattern and combine with OR
    val conditions = patterns.map {
      case AST.ExpressionPattern(expr) =>
        val exprOpt = typed(expr, context)
        val e = exprOpt.getOrElse(null)
        if (e == null) return (null, None)

        if (!TypeRules.isAssignable(conditionType, e.`type`)) {
          report(INCOMPATIBLE_TYPE, expr, conditionType, e.`type`)
          return (null, None)
        }

        val normalizedExpr =
          if (e.isBasicType && e.`type` != conditionType) new AsInstanceOf(e, conditionType)
          else if (e.isReferenceType && e.`type` != rootClass) new AsInstanceOf(e, rootClass)
          else e

        if (normalizedExpr.isReferenceType) {
          body.createEqualsForRef(new RefLocal(bind), normalizedExpr)
        } else {
          new BinaryTerm(BinaryTerm.Constants.EQUAL, BasicType.BOOLEAN, new RefLocal(bind), normalizedExpr)
        }

      case tp @ AST.TypePattern(_, name, typeRef) =>
        val mappedType = mapFrom(typeRef)
        if (mappedType == null) return (null, None)

        if (!mappedType.isObjectType) {
          report(INCOMPATIBLE_TYPE, tp, table_.rootClass, mappedType)
          return (null, None)
        }

        // Create instanceof check
        val instanceOfCheck = new InstanceOf(new RefLocal(bind), mappedType.asInstanceOf[ObjectType])

        // Store type pattern info (name and type) - variable will be registered later
        typePatternInfo = Some((name, mappedType))

        instanceOfCheck
    }

    if (conditions.isEmpty) return (null, None)

    // Combine conditions with OR
    val combined = conditions.reduceLeft { (acc, cond) =>
      new BinaryTerm(BinaryTerm.Constants.LOGICAL_OR, BasicType.BOOLEAN, acc, cond)
    }

    (combined, typePatternInfo)
  }

  def typeTryExpression(node: AST.TryExpression, context: LocalContext): Option[Term] = {
    // リソースを処理（try-with-resources）
    val resourceBindings = scala.collection.mutable.ArrayBuffer[(ClosureLocalBinding, Term)]()
    val autoCloseable = load("java.lang.AutoCloseable")
    var resourceFailed = false

    context.openScope {
      for (resource <- node.resources) {
        val initOpt = typed(resource.init, context)
        if (initOpt.isEmpty) {
          resourceFailed = true
        } else {
          val init = initOpt.get
          val resourceType =
            if (resource.typeRef != null) mapFrom(resource.typeRef)
            else init.`type`

          if (resourceType != null) {
            // Always add the variable to scope so it can be referenced in try block
            val index = context.add(resource.name, resourceType, isMutable = false)
            val binding = new ClosureLocalBinding(0, index, resourceType, isMutable = false)

            // AutoCloseableを実装しているか確認
            if (!TypeRules.isSuperType(autoCloseable, resourceType)) {
              report(INCOMPATIBLE_TYPE, resource, autoCloseable, resourceType)
              resourceFailed = true
            } else {
              resourceBindings += ((binding, init))
            }
          } else {
            resourceFailed = true
          }
        }
      }

      if (resourceFailed) return None

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

      // Type check finally block if present
      val finallyStmt: ActionStatement =
        if (node.finBlock != null) {
          typeBlockExpression(node.finBlock, context) match {
            case Some(finallyTerm) =>
              termToStatement(node.finBlock, finallyTerm)
            case None =>
              null
          }
        } else {
          null
        }

      val statement = new Try(node.location, resourceBindings.toArray, tryStmt, binds, catchStatements, finallyStmt)
      if (resultVar == null) {
        Some(statementTerm(statement, resultType, node.location))
      } else {
        Some(new Begin(node.location, Array[Term](statementTerm(statement, BasicType.VOID, node.location), new RefLocal(resultVar))))
      }
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

  /** Ensures the term is boolean, unboxing if needed. Returns the term (possibly unboxed). */
  private def ensureBoolean(node: AST.Node, term: Term): Term = {
    if (term == null) return null
    var result = term
    // Try to unbox Boolean wrapper type
    if (!result.isBasicType) {
      Boxing.unboxedType(table_, result.`type`) match {
        case Some(BasicType.BOOLEAN) => result = Boxing.unboxing(table_, result, BasicType.BOOLEAN)
        case _ => // will fail below
      }
    }
    if (result.`type` != BasicType.BOOLEAN) {
      report(INCOMPATIBLE_TYPE, node, BasicType.BOOLEAN, result.`type`)
    }
    result
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
