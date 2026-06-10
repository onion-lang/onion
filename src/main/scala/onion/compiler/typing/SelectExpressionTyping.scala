package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.TypedAST.BinaryTerm.Kind.*
import onion.compiler.typing.session.TypingBodyContext

import scala.collection.mutable.Buffer
import scala.util.boundary, boundary.break

/**
 * Types `select` expressions: pattern matching with guards, destructuring,
 * and exhaustiveness checking for sealed types.
 */
final class SelectExpressionTyping(
  private val typing: Typing,
  private val bodyContext: TypingBodyContext,
  private val body: TypingBodyPass,
  private val control: ControlExpressionTyping
) {

  private val destructuringProcessor = new DestructuringPatternProcessor(typing)

  def typeSelectExpression(node: AST.SelectExpression, context: LocalContext): Option[Term] = boundary {
    val condition = typed(node.condition, context).getOrElse(break(None))

    val name = context.newName
    val index = context.add(name, condition.`type`, isMutable = true)
    val bind = context.lookup(name)

    val caseConditions = Buffer[Term]()
    val caseTerms = Buffer[Term]()
    val caseNodes = Buffer[AST.BlockExpression]()
    // Store binding info along with created ClosureLocalBindings for code generation
    val caseBindingData = Buffer[(PatternBindingInfo, List[ClosureLocalBinding], Option[GuardInfo])]()
    var hasWildcardPattern = false // Track if any pattern has a wildcard

    for ((patterns, thenBlock) <- node.cases) {
      val (cond, bindingInfo, hasWildcard, guardInfo) = processPatterns(patterns.toArray, condition.`type`, bind, context)
      if (hasWildcard) hasWildcardPattern = true
      if (cond == null) break(None)

      // Combine base condition with nested conditions if any
      val combinedCond = bindingInfo match {
        case MultiBindings(_, _, nestedConds) if nestedConds.nonEmpty =>
          nestedConds.foldLeft(cond) { (acc, nested) =>
            new BinaryTerm(LOGICAL_AND, BasicType.BOOLEAN, acc, nested)
          }
        case _ => cond
      }
      caseConditions += combinedCond

      // Handle different binding types
      bindingInfo match {
        case SingleBinding(varName, varType) =>
          context.openScope {
            val varIndex = context.add(varName, varType, isMutable = false)
            val varBind = new ClosureLocalBinding(0, varIndex, varType, isMutable = false, isBoxed = false)
            // Type the guard in the same scope where the binding is set up
            val typedGuardInfo = guardInfo.map { case GuardInfo(guardAst, _) =>
              val guardTermOpt = typed(guardAst, context)
              val guardTerm = ensureBoolean(guardAst, guardTermOpt.getOrElse(null))
              GuardInfo(guardAst, guardTerm)
            }
            caseBindingData += ((bindingInfo, List(varBind), typedGuardInfo))
            // A type pattern without guard that matches the condition type is a catch-all
            if (typedGuardInfo.isEmpty && TypeRules.isSuperType(varType, condition.`type`)) {
              hasWildcardPattern = true
            }
            typeBlockExpression(thenBlock, context) match {
              case Some(term) =>
                caseTerms += term
                caseNodes += thenBlock
              case None => break(None)
            }
          }

        case MultiBindings(recordType, bindings, _) =>
          context.openScope {
            val varBinds = bindings.map { case BindingEntry(varName, varType, _) =>
              val varIndex = context.add(varName, varType, isMutable = false)
              new ClosureLocalBinding(0, varIndex, varType, isMutable = false, isBoxed = false)
            }
            // Type the guard in the same scope where the bindings are set up
            val typedGuardInfo = guardInfo.map { case GuardInfo(guardAst, _) =>
              val guardTermOpt = typed(guardAst, context)
              val guardTerm = ensureBoolean(guardAst, guardTermOpt.getOrElse(null))
              GuardInfo(guardAst, guardTerm)
            }
            caseBindingData += ((bindingInfo, varBinds, typedGuardInfo))
            // A destructuring pattern without guard that matches the condition type is a catch-all
            // (bindings don't affect whether the pattern is exhaustive - Box(v) catches all Box instances)
            if (typedGuardInfo.isEmpty && TypeRules.isSuperType(recordType, condition.`type`)) {
              hasWildcardPattern = true
            }
            typeBlockExpression(thenBlock, context) match {
              case Some(term) =>
                caseTerms += term
                caseNodes += thenBlock
              case None => break(None)
            }
          }

        case NoBindings =>
          // Type the guard (no bindings needed)
          val typedGuardInfo = guardInfo.map { case GuardInfo(guardAst, _) =>
            val guardTermOpt = typed(guardAst, context)
            val guardTerm = ensureBoolean(guardAst, guardTermOpt.getOrElse(null))
            GuardInfo(guardAst, guardTerm)
          }
          caseBindingData += ((NoBindings, Nil, typedGuardInfo))
          typeBlockExpression(thenBlock, context) match {
            case Some(term) =>
              caseTerms += term
              caseNodes += thenBlock
            case None => break(None)
          }
      }
    }

    // Exhaustiveness check for sealed types
    var isExhaustive = hasWildcardPattern // Wildcard pattern makes the match exhaustive
    if (node.elseBlock == null && !hasWildcardPattern) {
      condition.`type` match {
        case classDef: ClassDefinition if classDef.isSealed =>
          // Extract matched types from caseBindingData, excluding guarded patterns
          // (guarded patterns can fail at runtime, so they don't guarantee exhaustiveness)
          val unguardedMatchedTypes = caseBindingData.flatMap {
            case (SingleBinding(_, tp), _, None) => Some(tp)      // Type pattern without guard
            case (MultiBindings(tp, _, _), _, None) => Some(tp)   // Destructuring without guard
            case _ => None                                         // Guarded or NoBindings
          }

          if (unguardedMatchedTypes.nonEmpty) {
            val sealedSubtypes = classDef.sealedSubtypes
            val missingTypes = sealedSubtypes.filterNot { subtype =>
              unguardedMatchedTypes.exists { matchedType =>
                isSubtypeMatch(matchedType, subtype)
              }
            }
            if (missingTypes.nonEmpty) {
              bodyContext.report(NON_EXHAUSTIVE_PATTERN_MATCH, node, condition.`type`, missingTypes.map(_.asInstanceOf[Type]))
            } else {
              isExhaustive = true
            }
          }
        case _ =>
      }
    }

    val elseTermOpt = Option(node.elseBlock).map(typeBlockExpression(_, context)).getOrElse(Some(statementTerm(new NOP(node.location), BasicType.VOID, node.location)))
    val elseTerm = elseTermOpt.getOrElse(null)
    if (elseTerm == null) break(None)

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
    if (resultType == null) break(None)

    val resultVar =
      if (resultType.isBottomType || resultType == BasicType.VOID) null
      else new ClosureLocalBinding(0, context.add(context.newName, resultType, isMutable = true), resultType, isMutable = true)

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

    // Build case statements using nested if-else structure
    // Guards are checked AFTER bindings are set up, with fall-through to remaining cases on guard failure
    def buildCaseBranches(caseIndex: Int, fallback: ActionStatement): ActionStatement = {
      if (caseIndex >= caseTerms.length) {
        fallback
      } else {
        val term = caseTerms(caseIndex)
        val baseStmt = if (resultVar == null) termToStatement(caseNodes(caseIndex), term)
                       else assignBranch(caseNodes(caseIndex), term, resultVar, resultType)

        val (bindingInfo, varBinds, guardInfo) = caseBindingData(caseIndex)

        // Build the inner body (after bindings are set up)
        val innerBody: ActionStatement = guardInfo match {
          case Some(GuardInfo(_, guardTerm)) if guardTerm != null =>
            // Guard check: if guard passes, execute body; else fall through to remaining cases
            val restOfCases = buildCaseBranches(caseIndex + 1, fallback)
            new IfStatement(guardTerm, baseStmt, restOfCases)
          case _ =>
            baseStmt
        }

        val caseBody: ActionStatement = bindingInfo match {
          case SingleBinding(_, varType) =>
            val varBind = varBinds.head
            val cast = new AsInstanceOf(new RefLocal(bind), varType)
            val setVar = new ExpressionActionStatement(new SetLocal(varBind, cast))
            new StatementBlock(caseNodes(caseIndex).location, setVar, innerBody)

          case MultiBindings(recordType, bindings, nestedConditions) =>
            val castValue = new AsInstanceOf(new RefLocal(bind), recordType)
            val castVar = new ClosureLocalBinding(0, context.add(context.newName, recordType, isMutable = false), recordType, isMutable = false)
            val setCast = new ExpressionActionStatement(new SetLocal(castVar, castValue))

            // Build accessor for each binding following the access path
            def buildAccessor(base: Term, path: List[AccessStep]): Term = path match {
              case Nil => base
              case AccessStep(castType, getter) :: rest =>
                val cast = new AsInstanceOf(base, castType)
                if (getter == null) buildAccessor(cast, rest)
                else {
                  val call = new Call(cast, getter, Array.empty)
                  buildAccessor(call, rest)
                }
            }

            val bindingStmts = varBinds.zip(bindings).map { case (varBind, BindingEntry(_, _, accessPath)) =>
              val accessor = buildAccessor(new RefLocal(bind), accessPath)
              new ExpressionActionStatement(new SetLocal(varBind, accessor))
            }
            new StatementBlock(caseNodes(caseIndex).location, (setCast +: bindingStmts :+ innerBody)*)

          case NoBindings => innerBody
        }

        // Build: if (patternCond) { caseBody } else { restOfCases }
        val restOfCases = buildCaseBranches(caseIndex + 1, fallback)
        new IfStatement(caseConditions(caseIndex), caseBody, restOfCases)
      }
    }

    val branches: ActionStatement = {
      val fallback = if (elseStatement == null && caseTerms.isEmpty) new NOP(node.location) else elseStatement
      buildCaseBranches(0, fallback)
    }

    val init = new ExpressionActionStatement(new SetLocal(0, index, condition.`type`, condition))
    val statement = new StatementBlock(condition.location, init, branches)

    if (resultVar == null) {
      Some(statementTerm(statement, resultType, node.location))
    } else {
      Some(new Begin(node.location, Array[Term](statementTerm(statement, BasicType.VOID, node.location), new RefLocal(resultVar))))
    }
  }

  /** Delegate to DestructuringPatternProcessor */
  private def processDestructuringPattern(
    dp: AST.DestructuringPattern,
    bind: ClosureLocalBinding,
    context: LocalContext
  ): Option[(Term, PatternBindingInfo)] =
    destructuringProcessor.process(dp, bind, context)

  /** Returns (condition, pattern binding info, hasWildcard, optional guard info) */
  private def processPatterns(patterns: Array[AST.Pattern], conditionType: Type, bind: ClosureLocalBinding, context: LocalContext): (Term, PatternBindingInfo, Boolean, Option[GuardInfo]) = boundary {
    var bindingInfo: PatternBindingInfo = NoBindings
    var hasWildcard = false
    var guardInfo: Option[GuardInfo] = None

    // Process each pattern and combine with OR
    val conditions = patterns.map {
      case AST.WildcardPattern(loc) =>
        // Wildcard matches everything
        hasWildcard = true
        new BoolValue(loc, true)

      case AST.ExpressionPattern(expr) =>
        val exprOpt = typed(expr, context)
        val typedExpr = exprOpt.getOrElse(null)
        if (typedExpr == null) break((null, NoBindings, false, None))

        if (!TypeRules.isAssignable(conditionType, typedExpr.`type`)) {
          bodyContext.report(INCOMPATIBLE_TYPE, expr, conditionType, typedExpr.`type`)
          break((null, NoBindings, false, None))
        }

        val normalizedExpr =
          if (typedExpr.isBasicType && typedExpr.`type` != conditionType) new AsInstanceOf(typedExpr, conditionType)
          else if (typedExpr.isReferenceType && typedExpr.`type` != bodyContext.rootClass) new AsInstanceOf(typedExpr, bodyContext.rootClass)
          else typedExpr

        if (normalizedExpr.isReferenceType) {
          body.createEqualsForRef(new RefLocal(bind), normalizedExpr)
        } else {
          new BinaryTerm(EQUAL, BasicType.BOOLEAN, new RefLocal(bind), normalizedExpr)
        }

      case typePattern @ AST.TypePattern(_, name, typeRef) =>
        val mappedType = typing.mapFrom(typeRef)
        if (mappedType == null) break((null, NoBindings, false, None))

        if (!mappedType.isObjectType) {
          bodyContext.report(INCOMPATIBLE_TYPE, typePattern, bodyContext.table.rootClass, mappedType)
          break((null, NoBindings, false, None))
        }

        // Create instanceof check
        val instanceOfCheck = new InstanceOf(new RefLocal(bind), mappedType.asInstanceOf[ObjectType])

        // Store type pattern info (name and type) - variable will be registered later
        bindingInfo = SingleBinding(name, mappedType)

        instanceOfCheck

      case AST.BindingPattern(loc, name) =>
        // Simple binding pattern - matches everything and binds the value
        hasWildcard = true
        bindingInfo = SingleBinding(name, conditionType)
        new BoolValue(loc, true)

      case destructuringPattern: AST.DestructuringPattern =>
        processDestructuringPattern(destructuringPattern, bind, context) match {
          case Some((check, info)) =>
            bindingInfo = info
            check
          case None =>
            break((null, NoBindings, false, None))
        }

      case AST.GuardedPattern(loc, innerPattern, guard) =>
        // Process the inner pattern recursively
        val (innerCond, innerBindingInfo, innerHasWildcard, _) = processPatterns(Array(innerPattern), conditionType, bind, context)
        if (innerCond == null) break((null, NoBindings, false, None))

        // Inherit binding info from inner pattern
        bindingInfo = innerBindingInfo
        if (innerHasWildcard) hasWildcard = true

        // Type the guard expression in a temporary scope to verify it's valid and boolean
        // We need to temporarily add bindings to context for type checking
        val guardTermOpt = innerBindingInfo match {
          case SingleBinding(varName, varType) =>
            context.openScope {
              context.add(varName, varType, isMutable = false)
              typed(guard, context)
            }
          case MultiBindings(recordType, bindings, _) =>
            context.openScope {
              bindings.foreach { case BindingEntry(varName, varType, _) =>
                context.add(varName, varType, isMutable = false)
              }
              typed(guard, context)
            }
          case NoBindings =>
            typed(guard, context)
        }

        val guardTerm = guardTermOpt.getOrElse(null)
        if (guardTerm == null) break((null, NoBindings, false, None))

        // Ensure guard is boolean
        val checkedGuard = ensureBoolean(guard, guardTerm)
        if (checkedGuard == null) break((null, NoBindings, false, None))

        // Store the AST expression - it will be re-typed with actual bindings later
        // The guard check will happen after bindings are set up at code generation time
        guardInfo = Some(GuardInfo(guard))
        innerCond
    }

    if (conditions.isEmpty) break((null, NoBindings, false, None))

    // Combine conditions with OR
    val combined = conditions.reduceLeft { (acc, cond) =>
      new BinaryTerm(LOGICAL_OR, BasicType.BOOLEAN, acc, cond)
    }

    (combined, bindingInfo, hasWildcard, guardInfo)
  }

  /**
   * Check if a matched type covers a sealed subtype.
   * Used for exhaustiveness checking of sealed types.
   */
  private def isSubtypeMatch(matched: Type, sealedSubtype: ClassType): Boolean = {
    matched match {
      case matchedClassType: ClassType =>
        // Match by name (most common case) or check subtype relationship
        matchedClassType.name == sealedSubtype.name || TypeRules.isSuperType(matchedClassType, sealedSubtype)
      case _ => false
    }
  }

  private def typed(node: AST.Expression, context: LocalContext): Option[Term] =
    body.typed(node, context)

  private def typeBlockExpression(node: AST.BlockExpression, context: LocalContext): Option[Term] =
    control.typeBlockExpression(node, context)

  private def ensureBoolean(node: AST.Node, term: Term): Term =
    control.ensureBoolean(node, term)

  private def termToStatement(node: AST.Node, term: Term): ActionStatement =
    control.termToStatement(node, term)

  private def assignBranch(node: AST.Node, term: Term, resultVar: ClosureLocalBinding, resultType: Type): ActionStatement =
    control.assignBranch(node, term, resultVar, resultType)

  private def statementTerm(statement: ActionStatement, termType: Type, location: Location): Term =
    control.statementTerm(statement, termType, location)

  private def foldLub(node: AST.Node, types: Seq[Type]): Option[Type] =
    control.foldLub(node, types)
}
