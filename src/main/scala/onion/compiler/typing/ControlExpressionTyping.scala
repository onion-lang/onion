package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.TypedAST.BinaryTerm.Kind.*
import onion.compiler.toolbox.Boxing

import scala.collection.mutable.Buffer
import scala.util.boundary, boundary.break

final class ControlExpressionTyping(private val typing: Typing, private val body: TypingBodyPass) {
  import typing.*

  // Pattern binding info for different pattern types
  sealed trait PatternBindingInfo
  case object NoBindings extends PatternBindingInfo
  case class SingleBinding(name: String, tp: Type) extends PatternBindingInfo
  // For nested patterns, we store the access path from root to each binding
  case class AccessStep(castType: ClassType, getter: Method)
  case class BindingEntry(name: String, tp: Type, accessPath: List[AccessStep])
  case class MultiBindings(rootType: ClassType, bindings: List[BindingEntry], nestedConditions: List[Term] = Nil) extends PatternBindingInfo

  // Guard info - stores both AST (for validation) and typed term (for code generation)
  // The typed term is created in the same scope where the pattern bindings are set up
  case class GuardInfo(guardAst: AST.Expression, guardTerm: Term = null)

  // Smart cast: type narrowing information for if-expressions
  case class NarrowingInfo(
    positive: Map[String, Type],  // Narrowings for then-branch
    negative: Map[String, Type]   // Narrowings for else-branch
  )
  object NarrowingInfo {
    val empty: NarrowingInfo = NarrowingInfo(Map.empty, Map.empty)
  }

  /**
   * Extracts type narrowing information from a condition expression.
   * Used for smart casts in if-expressions.
   * Package-private for use by StatementTyping.
   */
  private[typing] def extractNarrowing(condition: AST.Expression, context: LocalContext): NarrowingInfo = {
    condition match {
      // x is SomeType -> narrow x to SomeType in then-branch
      case AST.IsInstance(_, AST.Id(_, name), typeRef) =>
        val binding = context.lookup(name)
        if (binding != null && !binding.isMutable) {
          val targetType = mapFrom(typeRef)
          if (targetType != null) {
            return NarrowingInfo(Map(name -> targetType), Map.empty)
          }
        }
        NarrowingInfo.empty

      // x != null -> narrow x from T? to T in then-branch
      case AST.NotEqual(_, AST.Id(_, name), AST.NullLiteral(_)) =>
        extractNullCheckNarrowing(name, context, positive = true)
      case AST.NotEqual(_, AST.NullLiteral(_), AST.Id(_, name)) =>
        extractNullCheckNarrowing(name, context, positive = true)

      // x == null -> narrow x from T? to T in else-branch
      case AST.Equal(_, AST.Id(_, name), AST.NullLiteral(_)) =>
        extractNullCheckNarrowing(name, context, positive = false)
      case AST.Equal(_, AST.NullLiteral(_), AST.Id(_, name)) =>
        extractNullCheckNarrowing(name, context, positive = false)

      // cond1 && cond2 -> both narrowings apply in then-branch
      case AST.LogicalAnd(_, left, right) =>
        val leftNarrowing = extractNarrowing(left, context)
        val rightNarrowing = extractNarrowing(right, context)
        NarrowingInfo(leftNarrowing.positive ++ rightNarrowing.positive, Map.empty)

      case _ => NarrowingInfo.empty
    }
  }

  /**
   * Helper for null-check narrowing extraction.
   */
  private def extractNullCheckNarrowing(name: String, context: LocalContext, positive: Boolean): NarrowingInfo = {
    val binding = context.lookup(name)
    if (binding != null && !binding.isMutable) {
      binding.tp match {
        case nullableType: NullableType =>
          if (positive) {
            NarrowingInfo(Map(name -> nullableType.innerType), Map.empty)
          } else {
            NarrowingInfo(Map.empty, Map(name -> nullableType.innerType))
          }
        case _ => NarrowingInfo.empty
      }
    } else {
      NarrowingInfo.empty
    }
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

  def typeIfExpression(node: AST.IfExpression, context: LocalContext): Option[Term] = {
    context.openScope {
      // Type the condition
      val conditionRawOpt = typed(node.condition, context)
      if (conditionRawOpt.isEmpty) return None
      val conditionRaw = conditionRawOpt.get
      val condition = ensureBoolean(node.condition, conditionRaw)
      if (condition == null) return None

      // Extract smart cast narrowing info
      val narrowing = extractNarrowing(node.condition, context)
      val savedNarrowings = context.saveNarrowings()

      // Type the then-block with positive narrowings applied
      narrowing.positive.foreach { case (name, narrowedType) =>
        context.addNarrowing(name, narrowedType)
      }
      val thenTermOpt = typeBlockExpression(node.thenBlock, context)
      context.restoreNarrowings(savedNarrowings)

      if (thenTermOpt.isEmpty) return None
      val thenTerm = thenTermOpt.get

      if (node.elseBlock == null) {
        val thenStmt = termToStatement(node.thenBlock, thenTerm)
        Some(statementTerm(new IfStatement(condition, thenStmt, null), BasicType.VOID, node.location))
      } else {
        // Type the else-block with negative narrowings applied
        narrowing.negative.foreach { case (name, narrowedType) =>
          context.addNarrowing(name, narrowedType)
        }
        val elseTermOpt = typeBlockExpression(node.elseBlock, context)
        context.restoreNarrowings(savedNarrowings)

        if (elseTermOpt.isEmpty) return None
        val elseTerm = elseTermOpt.get

        val resultType = leastUpperBound(node, thenTerm.`type`, elseTerm.`type`)
        if (resultType == null) return None

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
              report(NON_EXHAUSTIVE_PATTERN_MATCH, node, condition.`type`, missingTypes.map(_.asInstanceOf[Type]))
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

  /** Find the getter method for a field in a record type */
  private def findFieldGetter(classDef: ClassDefinition, fieldName: String): Option[Method] =
    val getters = classDef.methods(fieldName)
    if getters.isEmpty then None
    else Some(getters.find(m => m.arguments.isEmpty).getOrElse(getters.head))

  /** Process a destructuring pattern - returns (instanceOfCheck, bindingInfo) on success, None on error */
  private def processDestructuringPattern(
    dp: AST.DestructuringPattern,
    bind: ClosureLocalBinding,
    context: LocalContext
  ): Option[(Term, PatternBindingInfo)] = boundary {
    val AST.DestructuringPattern(_, constructor, fieldPatterns) = dp

    def resolveRecordClass(node: AST.Node, name: String): ClassDefinition = {
      val recordType = load(name)
      recordType match {
        case null =>
          report(NOT_A_RECORD_TYPE, node, name)
          break(None)
        case classDef: ClassDefinition =>
          classDef
        case _ =>
          report(NOT_A_RECORD_TYPE, node, name)
          break(None)
      }
    }

    // Recursive helper to process nested field patterns at any depth
    def processNestedFieldPattern(
      fieldPattern: AST.Pattern,
      fieldType: Type,
      currentPath: List[AccessStep],
      bindingEntries: scala.collection.mutable.ArrayBuffer[BindingEntry],
      nestedConditions: scala.collection.mutable.ArrayBuffer[Term],
      parentNode: AST.Node
    ): Unit = fieldPattern match {
      case AST.WildcardPattern(_) =>
        // Skip wildcard - no binding created

      case AST.BindingPattern(_, bindingName) =>
        // Simple binding
        bindingEntries += BindingEntry(bindingName, fieldType, currentPath)

      case nested @ AST.DestructuringPattern(_, ctorName, fieldPats) =>
        // Nested destructuring pattern - recurse to any depth
        val nestedClassDef = resolveRecordClass(nested, ctorName)
        val nestedType = nestedClassDef

        val nestedFields = nestedClassDef.fields
        if (fieldPats.length != nestedFields.length) {
          report(WRONG_BINDING_COUNT, nested, Int.box(nestedFields.length), Int.box(fieldPats.length), ctorName)
          break(None)
        }

        // Build accessor for nested type check by following the access path
        def buildAccessorForCondition(base: Term, path: List[AccessStep]): Term = path match {
          case Nil => base
          case AccessStep(castType, getter) :: rest =>
            val cast = new AsInstanceOf(base, castType)
            if (getter == null) buildAccessorForCondition(cast, rest)
            else {
              val call = new Call(cast, getter, Array.empty)
              buildAccessorForCondition(call, rest)
            }
        }

        val fieldAccess = buildAccessorForCondition(new RefLocal(bind), currentPath)
        val nestedInstanceOf = new InstanceOf(fieldAccess, nestedType.asInstanceOf[ObjectType])
        nestedConditions += nestedInstanceOf

        // Process nested field patterns recursively
        for ((nestedFieldPat, nestedField) <- fieldPats.zip(nestedFields)) {
          val nestedGetter = findFieldGetter(nestedClassDef, nestedField.name).getOrElse {
            report(NOT_A_RECORD_TYPE, nested, ctorName)
            break(None)
          }
          val nestedPath = currentPath :+ AccessStep(nestedType.asInstanceOf[ClassType], nestedGetter)
          processNestedFieldPattern(nestedFieldPat, nestedField.`type`, nestedPath, bindingEntries, nestedConditions, nested)
        }

      case other =>
        // Other patterns not supported in destructuring position
        report(NOT_A_RECORD_TYPE, parentNode, s"unsupported pattern type in destructuring: ${other.getClass.getSimpleName}")
        break(None)
    }

    // Look up the record type by constructor name
    val classDef = resolveRecordClass(dp, constructor)
    val recordType = classDef

    // Get fields in order (records store fields in insertion order)
    val fields = classDef.fields
    val fieldCount = fields.length

    // Check binding count matches field count
    if (fieldPatterns.length != fieldCount) {
      report(WRONG_BINDING_COUNT, dp, Int.box(fieldCount), Int.box(fieldPatterns.length), constructor)
      break(None)
    }

    // Create instanceof check
    val instanceOfCheck = new InstanceOf(new RefLocal(bind), recordType.asInstanceOf[ObjectType])

    // Process each field pattern recursively
    val bindingEntries = scala.collection.mutable.ArrayBuffer[BindingEntry]()
    val nestedConditions = scala.collection.mutable.ArrayBuffer[Term]()

    for ((fieldPattern, field) <- fieldPatterns.zip(fields)) {
      val getter = findFieldGetter(classDef, field.name).getOrElse {
        report(NOT_A_RECORD_TYPE, dp, constructor)
        break(None)
      }
      val currentPath = List(AccessStep(recordType.asInstanceOf[ClassType], getter))
      processNestedFieldPattern(fieldPattern, field.`type`, currentPath, bindingEntries, nestedConditions, dp)
    }

    val bindingInfo = MultiBindings(recordType.asInstanceOf[ClassType], bindingEntries.toList, nestedConditions.toList)
    Some((instanceOfCheck, bindingInfo))
  }

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
          report(INCOMPATIBLE_TYPE, expr, conditionType, typedExpr.`type`)
          break((null, NoBindings, false, None))
        }

        val normalizedExpr =
          if (typedExpr.isBasicType && typedExpr.`type` != conditionType) new AsInstanceOf(typedExpr, conditionType)
          else if (typedExpr.isReferenceType && typedExpr.`type` != rootClass) new AsInstanceOf(typedExpr, rootClass)
          else typedExpr

        if (normalizedExpr.isReferenceType) {
          body.createEqualsForRef(new RefLocal(bind), normalizedExpr)
        } else {
          new BinaryTerm(EQUAL, BasicType.BOOLEAN, new RefLocal(bind), normalizedExpr)
        }

      case typePattern @ AST.TypePattern(_, name, typeRef) =>
        val mappedType = mapFrom(typeRef)
        if (mappedType == null) break((null, NoBindings, false, None))

        if (!mappedType.isObjectType) {
          report(INCOMPATIBLE_TYPE, typePattern, table_.rootClass, mappedType)
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

  def typeTryExpression(node: AST.TryExpression, context: LocalContext): Option[Term] = boundary {
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

      if (resourceFailed) break(None)

      val tryTermOpt = typeBlockExpression(node.tryBlock, context)
      val tryTerm = tryTermOpt.getOrElse(null)
      if (tryTerm == null) break(None)

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
      if (failed) break(None)

      val resultType =
        if (catchTerms.isEmpty) tryTerm.`type`
        else {
          val types = tryTerm.`type` +: catchTerms.map(_.`type`).toSeq
          foldLub(node, types).orNull
        }
      if (resultType == null) break(None)

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

  def typeSynchronizedExpression(node: AST.SynchronizedExpression, context: LocalContext): Option[Term] = boundary {
    context.openScope {
      // Type the lock expression
      val lock = typed(node.condition, context).getOrElse(break(None))

      // Lock must be object type, not primitive
      if (lock.isBasicType) {
        report(INCOMPATIBLE_TYPE, node.condition, load("java.lang.Object"), lock.`type`)
        break(None)
      }

      // Type the body as an expression (returns value)
      val bodyTerm = typeBlockExpression(node.block, context).getOrElse(break(None))

      // Create SynchronizedTerm that returns the body's value
      Some(new SynchronizedTerm(node.location, lock, bodyTerm))
    }
  }

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
    // Try to unbox Boolean wrapper type
    val result = if (!term.isBasicType) {
      Boxing.unboxedType(table_, term.`type`) match {
        case Some(BasicType.BOOLEAN) => Boxing.unboxing(table_, term, BasicType.BOOLEAN)
        case _ => term
      }
    } else term
    if (result.`type` != BasicType.BOOLEAN) {
      report(INCOMPATIBLE_TYPE, node, BasicType.BOOLEAN, result.`type`)
    }
    result
  }

  private def termToStatement(node: AST.Node, term: Term): ActionStatement = term match {
    case stmtTerm: StatementTerm => stmtTerm.statement
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

  private def foldLub(node: AST.Node, types: Seq[Type]): Option[Type] = boundary {
    types.reduceOption { (acc, t) =>
      val result = leastUpperBound(node, acc, t)
      if (result == null) break(None)
      result
    }
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
}
