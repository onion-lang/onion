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

  def typeSelectExpression(node: AST.SelectExpression, context: LocalContext): Option[Term] = boundary {
    val conditionOpt = typed(node.condition, context)
    val condition = conditionOpt.getOrElse(null)
    if (condition == null) break(None)

    val name = context.newName
    val index = context.add(name, condition.`type`, isMutable = true)
    val bind = context.lookup(name)

    val caseConditions = Buffer[Term]()
    val caseTerms = Buffer[Term]()
    val caseNodes = Buffer[AST.BlockExpression]()
    // Store binding info along with created ClosureLocalBindings for code generation
    val caseBindingData = Buffer[(PatternBindingInfo, List[ClosureLocalBinding], Option[GuardInfo])]()
    val matchedTypes = Buffer[Type]() // Track matched types for exhaustiveness check
    var hasWildcardPattern = false // Track if any pattern has a wildcard

    var failed = false
    val cases = node.cases.iterator
    while (cases.hasNext && !failed) {
      val (patterns, thenBlock) = cases.next()
      val (cond, bindingInfo, hasWildcard, guardInfo) = processPatterns(patterns.toArray, condition.`type`, bind, context)
      if (hasWildcard) hasWildcardPattern = true
      if (cond == null) {
        failed = true
      } else {
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
            matchedTypes += varType // Track for exhaustiveness
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
                case None => failed = true
              }
            }

          case MultiBindings(recordType, bindings, _) =>
            matchedTypes += recordType // Track for exhaustiveness
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
                case None => failed = true
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
              case None => failed = true
            }
        }
      }
    }
    if (failed) break(None)

    // Exhaustiveness check for sealed types
    var isExhaustive = hasWildcardPattern // Wildcard pattern makes the match exhaustive
    if (node.elseBlock == null && !hasWildcardPattern && matchedTypes.nonEmpty) {
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
        val e = exprOpt.getOrElse(null)
        if (e == null) break((null, NoBindings, false, None))

        if (!TypeRules.isAssignable(conditionType, e.`type`)) {
          report(INCOMPATIBLE_TYPE, expr, conditionType, e.`type`)
          break((null, NoBindings, false, None))
        }

        val normalizedExpr =
          if (e.isBasicType && e.`type` != conditionType) new AsInstanceOf(e, conditionType)
          else if (e.isReferenceType && e.`type` != rootClass) new AsInstanceOf(e, rootClass)
          else e

        if (normalizedExpr.isReferenceType) {
          body.createEqualsForRef(new RefLocal(bind), normalizedExpr)
        } else {
          new BinaryTerm(EQUAL, BasicType.BOOLEAN, new RefLocal(bind), normalizedExpr)
        }

      case tp @ AST.TypePattern(_, name, typeRef) =>
        val mappedType = mapFrom(typeRef)
        if (mappedType == null) break((null, NoBindings, false, None))

        if (!mappedType.isObjectType) {
          report(INCOMPATIBLE_TYPE, tp, table_.rootClass, mappedType)
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

      case dp @ AST.DestructuringPattern(_, constructor, fieldPatterns) =>
        // Look up the record type by constructor name
        val recordType = load(constructor)
        if (recordType == null) {
          report(NOT_A_RECORD_TYPE, dp, constructor)
          break((null, NoBindings, false, None))
        }

        val classDef = recordType match {
          case cd: ClassDefinition => cd
          case _ =>
            report(NOT_A_RECORD_TYPE, dp, constructor)
            break((null, NoBindings, false, None))
        }

        // Get fields in order (records store fields in insertion order)
        val fields = classDef.fields
        val fieldCount = fields.length

        // Check binding count matches field count
        if (fieldPatterns.length != fieldCount) {
          report(WRONG_BINDING_COUNT, dp, Int.box(fieldCount), Int.box(fieldPatterns.length), constructor)
          break((null, NoBindings, false, None))
        }

        // Create instanceof check
        val instanceOfCheck = new InstanceOf(new RefLocal(bind), recordType.asInstanceOf[ObjectType])

        // Process each field pattern recursively
        val bindingEntries = scala.collection.mutable.ArrayBuffer[BindingEntry]()
        val nestedConditions = scala.collection.mutable.ArrayBuffer[Term]()
        val rootAccessPath = List(AccessStep(recordType.asInstanceOf[ClassType], null)) // placeholder for root cast

        for ((fieldPattern, field) <- fieldPatterns.zip(fields)) {
          val getter = findFieldGetter(classDef, field.name).getOrElse {
            report(NOT_A_RECORD_TYPE, dp, constructor)
            break((null, NoBindings, false, None))
          }
          val currentPath = List(AccessStep(recordType.asInstanceOf[ClassType], getter))

          fieldPattern match {
            case AST.WildcardPattern(_) =>
              // Skip wildcard - no binding created

            case AST.BindingPattern(_, name) =>
              // Simple binding
              bindingEntries += BindingEntry(name, field.`type`, currentPath)

            case nested @ AST.DestructuringPattern(_, nestedCtor, nestedFieldPatterns) =>
              // Nested destructuring pattern - recurse
              val nestedType = load(nestedCtor)
              if (nestedType == null) {
                report(NOT_A_RECORD_TYPE, nested, nestedCtor)
                break((null, NoBindings, false, None))
              }

              val nestedClassDef = nestedType match {
                case cd: ClassDefinition => cd
                case _ =>
                  report(NOT_A_RECORD_TYPE, nested, nestedCtor)
                  break((null, NoBindings, false, None))
              }

              val nestedFields = nestedClassDef.fields
              if (nestedFieldPatterns.length != nestedFields.length) {
                report(WRONG_BINDING_COUNT, nested, Int.box(nestedFields.length), Int.box(nestedFieldPatterns.length), nestedCtor)
                break((null, NoBindings, false, None))
              }

              // Build accessor for nested type check: ((RootType)bind).getter() instanceof NestedType
              val rootCast = new AsInstanceOf(new RefLocal(bind), recordType.asInstanceOf[ObjectType])
              val fieldAccess = new Call(rootCast, getter, Array.empty)
              val nestedInstanceOf = new InstanceOf(fieldAccess, nestedType.asInstanceOf[ObjectType])
              nestedConditions += nestedInstanceOf

              // Process nested field patterns
              for ((nestedFieldPattern, nestedField) <- nestedFieldPatterns.zip(nestedFields)) {
                val nestedGetter = findFieldGetter(nestedClassDef, nestedField.name).getOrElse {
                  report(NOT_A_RECORD_TYPE, nested, nestedCtor)
                  break((null, NoBindings, false, None))
                }
                val nestedPath = currentPath :+ AccessStep(nestedType.asInstanceOf[ClassType], nestedGetter)

                nestedFieldPattern match {
                  case AST.WildcardPattern(_) =>
                    // Skip

                  case AST.BindingPattern(_, name) =>
                    bindingEntries += BindingEntry(name, nestedField.`type`, nestedPath)

                  case _ =>
                    // Deeper nesting - for now, report error (could support more levels)
                    report(NOT_A_RECORD_TYPE, nested, "deeply nested patterns not yet supported")
                    break((null, NoBindings, false, None))
                }
              }

            case other =>
              // Other patterns not supported in destructuring position
              report(NOT_A_RECORD_TYPE, dp, s"unsupported pattern type in destructuring: ${other.getClass.getSimpleName}")
              break((null, NoBindings, false, None))
          }
        }

        bindingInfo = MultiBindings(recordType.asInstanceOf[ClassType], bindingEntries.toList, nestedConditions.toList)
        instanceOfCheck

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
      val lock = typed(node.condition, context).getOrElse(null)
      if (lock == null) break(None)

      // Lock must be object type, not primitive
      if (lock.isBasicType) {
        report(INCOMPATIBLE_TYPE, node.condition, load("java.lang.Object"), lock.`type`)
        break(None)
      }

      // Type the body as an expression (returns value)
      val bodyTermOpt = typeBlockExpression(node.block, context)
      val bodyTerm = bodyTermOpt.getOrElse(null)
      if (bodyTerm == null) break(None)

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
