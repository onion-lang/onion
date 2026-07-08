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

  def typeSelectExpression(node: AST.SelectExpression, context: LocalContext, asStatement: Boolean = false, expected: Type = null): Option[Term] = boundary {
    // Thread the expected result type into each case/else branch so an
    // under-determined empty collection literal there (`case 0: []`) target-types
    // its element type from the expected type, mirroring if/else and argument/
    // block-trailing positions (issue #300). Only meaningful in EXPRESSION
    // position; in statement position the value is discarded, so passing it is
    // harmless. A null expected leaves branch typing unchanged.
    val branchExpected: Type =
      if (expected != null && !expected.isBottomType && expected != BasicType.VOID) expected else null
    val condition = typed(node.condition, context).getOrElse(break(None))

    val name = context.newName
    val index = context.add(name, condition.`type`, isMutable = true)
    val bind = context.lookup(name)

    // When the scrutinee is an enum, a bare `case CONST:` naming one of its
    // constants resolves to `EnumType::CONST` (unless a local variable of that
    // name shadows it). Rewriting up front keeps pattern matching and the
    // exhaustiveness check consistent.
    val cases = rewriteBareEnumConstants(node.cases, condition.`type`, context)

    val caseConditions = Buffer[Term]()
    val caseTerms = Buffer[Term]()
    val caseNodes = Buffer[AST.BlockExpression]()
    // Store binding info along with created ClosureLocalBindings for code generation
    val caseBindingData = Buffer[(PatternBindingInfo, List[ClosureLocalBinding], Option[GuardInfo])]()
    var hasWildcardPattern = false // Track if any pattern has a wildcard

    for ((patterns, thenBlock) <- cases) {
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
            typeCaseBodyWithGuardNarrowing(typedGuardInfo, thenBlock, context, branchExpected) match {
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
            typeCaseBodyWithGuardNarrowing(typedGuardInfo, thenBlock, context, branchExpected) match {
              case Some(term) =>
                caseTerms += term
                caseNodes += thenBlock
              case None => break(None)
            }
          }

        case RegexBindings(_, names) =>
          context.openScope {
            val stringType = bodyContext.load("java.lang.String")
            val varBinds = names.map { n =>
              val varIndex = context.add(n, stringType, isMutable = false)
              new ClosureLocalBinding(0, varIndex, stringType, isMutable = false, isBoxed = false)
            }
            val typedGuardInfo = guardInfo.map { case GuardInfo(guardAst, _) =>
              val guardTermOpt = typed(guardAst, context)
              val guardTerm = ensureBoolean(guardAst, guardTermOpt.getOrElse(null))
              GuardInfo(guardAst, guardTerm)
            }
            caseBindingData += ((bindingInfo, varBinds, typedGuardInfo))
            typeCaseBodyWithGuardNarrowing(typedGuardInfo, thenBlock, context, branchExpected) match {
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
          typeCaseBodyWithGuardNarrowing(typedGuardInfo, thenBlock, context, branchExpected) match {
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
        case classDef: ClassDefinition if Modifier.isEnum(classDef.modifier) =>
          // Value matches over an enum: exhaustive when every constant
          // appears in an unguarded Constant-reference case
          val allPatterns = cases.flatMap(_._1)
          val constantNames = classDef.fields.collect {
            case f if Modifier.isStatic(f.modifier) && Modifier.isFinal(f.modifier) && f.`type`.name == classDef.name => f.name
          }.toSeq
          val matchedNames = allPatterns.collect {
            case AST.ExpressionPattern(sel: AST.StaticMemberSelection) => sel.name
          }.toSet
          val allConstantRefs = allPatterns.forall {
            case AST.ExpressionPattern(_: AST.StaticMemberSelection) => true
            case _ => false
          }
          if (allConstantRefs && matchedNames.nonEmpty) {
            val missing = constantNames.filterNot(matchedNames.contains)
            if (missing.nonEmpty) {
              bodyContext.report(NON_EXHAUSTIVE_PATTERN_MATCH, node, condition.`type`, missing.toArray)
            } else {
              isExhaustive = true
            }
          }
        case _ =>
      }
    }

    val elseTermOpt = Option(node.elseBlock).map(typeBlockExpression(_, context, branchExpected)).getOrElse(Some(statementTerm(new NOP(node.location), BasicType.VOID, node.location)))
    val elseTerm = elseTermOpt.getOrElse(null)
    if (elseTerm == null) break(None)

    val resultType =
      if (node.elseBlock == null && !isExhaustive) BasicType.VOID
      else {
        // Unify the branch value types via LUB. In EXPRESSION position a failure
        // (e.g. a void branch where a value is required) is a real error and is
        // reported. In STATEMENT position the value is discarded, so — like an
        // if/else statement — the branches need not unify: a mix of value and
        // void branches is fine and the whole select degrades to a void
        // statement (issue #297). Each branch value is then dropped by
        // termToStatement (the resultVar == null path). A silent LUB is tried
        // first so an all-terminating (all-`return`) statement select still
        // yields BOTTOM and is recognized as definitely-returning (E0067).
        val branchTypes =
          if (node.elseBlock == null) caseTerms.map(_.`type`).toSeq
          else caseTerms.map(_.`type`).toSeq :+ elseTerm.`type`
        foldLubSilent(branchTypes) match {
          case Some(t) => t
          case None if asStatement => BasicType.VOID
          case None => foldLub(node, branchTypes).orNull // re-run to report the error
        }
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

          case RegexBindings(groupsVar, _) =>
            // The condition already stored the capture groups in groupsVar;
            // each binding reads its group from the array.
            val bindingStmts = varBinds.zipWithIndex.map { case (varBind, i) =>
              new ExpressionActionStatement(new SetLocal(varBind, new RefArray(new RefLocal(groupsVar), new IntValue(i))))
            }
            new StatementBlock(caseNodes(caseIndex).location, (bindingStmts :+ innerBody)*)

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
  /** Rewrite a bare `case CONST:` over an enum scrutinee into `EnumType::CONST`.
    * A local variable of the same name takes precedence (keeping the change
    * purely additive: only previously-unresolvable identifiers are rewritten). */
  private def rewriteBareEnumConstants(
    cases: List[(List[AST.Pattern], AST.BlockExpression)],
    conditionType: Type,
    context: LocalContext
  ): List[(List[AST.Pattern], AST.BlockExpression)] = {
    val enumDef: Option[ClassDefinition] = conditionType match {
      case cd: ClassDefinition if Modifier.isEnum(cd.modifier) => Some(cd)
      case ac: AppliedClassType => ac.raw match {
        case cd: ClassDefinition if Modifier.isEnum(cd.modifier) => Some(cd)
        case _ => None
      }
      case _ => None
    }
    enumDef match {
      case None => cases
      case Some(cd) =>
        val constants = cd.fields.collect {
          case f if Modifier.isStatic(f.modifier) && Modifier.isFinal(f.modifier) && f.`type`.name == cd.name => f.name
        }.toSet
        def rewrite(p: AST.Pattern): AST.Pattern = p match {
          case AST.ExpressionPattern(AST.Id(loc, idName)) if constants.contains(idName) && context.lookup(idName) == null =>
            AST.ExpressionPattern(new AST.StaticMemberSelection(loc, new AST.TypeNode(loc, new AST.ReferenceType(cd.name, true), false), idName))
          case other => other
        }
        cases.map { case (ps, blk) => (ps.map(rewrite), blk) }
    }
  }

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

        // An int case label matches a byte/short/char scrutinee (compared by value,
        // as Java's switch does); the narrowing cast below (`AsInstanceOf`) normalizes
        // it to the scrutinee type. Char scrutinees still also accept char literals.
        val narrowableIntLabel = typedExpr.`type` == BasicType.INT &&
          (conditionType == BasicType.BYTE || conditionType == BasicType.SHORT || conditionType == BasicType.CHAR)
        if (!TypeRules.isAssignable(conditionType, typedExpr.`type`) && !narrowableIntLabel) {
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
        val mappedType = typing.mapFrom(typeRef).getOrElse(break((null, NoBindings, false, None)))

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

      case rp @ AST.RegexPattern(loc, pattern, names) =>
        // case re"..." (g1, g2): the subject must be a (non-nullable) String.
        // The literal is validated here at compile time: a malformed pattern
        // and a group-count/binding-count mismatch are compile errors.
        val stringType = bodyContext.load("java.lang.String")
        if (!TypeRules.isAssignable(stringType, conditionType)) {
          bodyContext.report(INCOMPATIBLE_TYPE, rp, stringType, conditionType)
          break((null, NoBindings, false, None))
        }
        val compiled =
          try {
            java.util.regex.Pattern.compile(pattern)
          } catch {
            case e: java.util.regex.PatternSyntaxException =>
              bodyContext.report(REGEX_PATTERN_INVALID, rp, e.getDescription + " (at index " + e.getIndex + ")")
              break((null, NoBindings, false, None))
          }
        val groupCount = compiled.matcher("").groupCount()
        if (names.nonEmpty && names.size != groupCount) {
          bodyContext.report(REGEX_GROUP_MISMATCH, rp, groupCount.toString, names.size.toString)
          break((null, NoBindings, false, None))
        }
        val regexType = bodyContext.load("onion.Regex")
        val groupsMethod = regexType.methods("matchGroups").find { m =>
          m.arguments.length == 2 && Modifier.isStatic(m.modifier)
        }.getOrElse(break((null, NoBindings, false, None)))
        // Evaluate the groups once, as a side effect of the condition:
        //   (__g = Regex::matchGroups(subject, pattern)) != null
        // matchGroups is ANCHORED (the whole subject must match) and returns
        // null on no-match; the bindings then read __g[i] in the case body.
        val arrayType = groupsMethod.returnType
        val gVar = new ClosureLocalBinding(0, context.add(context.newName, arrayType, isMutable = true), arrayType, isMutable = true)
        val groupsCall = new CallStatic(regexType, groupsMethod,
          Array[Term](new RefLocal(bind), new StringValue(loc, pattern, stringType)))
        bindingInfo = RegexBindings(gVar, names)
        new Begin(loc, Array[Term](
          new SetLocal(gVar, groupsCall),
          new BinaryTerm(NOT_EQUAL, BasicType.BOOLEAN, new RefLocal(gVar), new NullValue(loc))
        ))

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
          case RegexBindings(_, names) =>
            context.openScope {
              val stringType = bodyContext.load("java.lang.String")
              names.foreach(n => context.add(n, stringType, isMutable = false))
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

  private def typeBlockExpression(node: AST.BlockExpression, context: LocalContext, expected: Type = null): Option[Term] =
    control.typeBlockExpression(node, context, expected)

  /**
   * Types a case body, first narrowing it by the positive facts of its `when`
   * guard (issue #304). A case body runs only when its guard is true, so the
   * guard narrows the body exactly like an `if`-then branch narrows its then
   * block: `case s when s != null: s.length()` narrows `s` to non-null.
   *
   * A `val`/parameter narrows outright (addNarrowing); a `var` narrows
   * flow-sensitively (addFlowNarrowing), which a reassignment in the body clears
   * from that point on. We exclude a var reassigned in the GUARD itself or in the
   * case BODY (matching the if-then handling of #288/#289/#294): such a var is not
   * reliably non-null at body entry. The narrowing is bounded by the surrounding
   * per-case scope (openScope) and this save/restore, so it never leaks to other
   * cases or past the select. A guard-less case narrows nothing.
   */
  private def typeCaseBodyWithGuardNarrowing(
    guardInfo: Option[GuardInfo],
    thenBlock: AST.BlockExpression,
    context: LocalContext,
    expected: Type
  ): Option[Term] = {
    guardInfo match {
      case Some(GuardInfo(guardAst, _)) =>
        val savedNarrowings = context.saveNarrowings()
        val narrowing = body.extractNarrowing(guardAst, context)
        val guardReassigned = AssignedVariableScanner.scan(guardAst)
        val bodyReassigned = AssignedVariableScanner.scan(thenBlock)
        narrowing.positive.foreach { case (name, tp) =>
          context.addNarrowing(name, tp)
        }
        narrowing.mutablePositive
          .filter { case (name, _) => !guardReassigned.contains(name) && !bodyReassigned.contains(name) }
          .foreach { case (name, tp) => context.addFlowNarrowing(name, tp) }
        val result = typeBlockExpression(thenBlock, context, expected)
        context.restoreNarrowings(savedNarrowings)
        result
      case None =>
        typeBlockExpression(thenBlock, context, expected)
    }
  }

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

  /** Like [[foldLub]] but reports nothing on failure — used to probe whether the
    * branch types unify before deciding, in statement position, to discard them.
    * Returns None if any pairwise LUB is undefined (e.g. a void/non-void mix). */
  private def foldLubSilent(types: Seq[Type]): Option[Type] = boundary {
    types.reduceOption { (acc, t) =>
      val result = TypeCheckingHelpers.leastUpperBound(bodyContext.table, null, acc, t, bodyContext.rootClass, (_, _, _) => ())
      if (result == null) break(None)
      result
    }
  }
}
