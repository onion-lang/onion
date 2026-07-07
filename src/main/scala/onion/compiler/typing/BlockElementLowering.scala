package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.TypedAST.BinaryTerm.Kind.*
import onion.compiler.typing.session.TypingBodyContext
import onion.compiler.toolbox.Boxing

import scala.collection.mutable.Buffer

final class BlockElementLowering(
  private val typing: Typing,
  private val bodyContext: TypingBodyContext,
  private val body: TypingBodyPass
) {

  /** Ensures the term is boolean, unboxing if needed. Returns the term (possibly unboxed) or null on error. */
  private def ensureBooleanCondition(node: AST.Node, termOpt: Option[Term]): Term =
    termOpt match {
      case None => null
      case Some(term) =>
        TypeCheckingHelpers.ensureBoolean(bodyContext.table, node, term,
          (n, actual) => bodyContext.report(INCOMPATIBLE_TYPE, n, BasicType.BOOLEAN, actual))
    }

  /**
   * Checks if a statement is "terminating" (never falls through to the next statement).
   */
  private def isTerminating(stmt: ActionStatement): Boolean = stmt match {
    case _: Return | _: Throw | _: Break | _: Continue => true
    case block: StatementBlock =>
      block.statements.nonEmpty && isTerminating(block.statements.last)
    case ifStmt: IfStatement =>
      // If statement only terminates if both branches exist and both terminate
      ifStmt.elseStatement != null &&
        isTerminating(ifStmt.thenStatement) &&
        isTerminating(ifStmt.elseStatement)
    case _ => false
  }

  def translate(node: AST.BlockElement, context: LocalContext): ActionStatement = node match {
    case AST.BlockExpression(loc, elements) =>
      context.openScope {
        // Guard-clause narrowings (see IfExpression) leak forward to later
        // statements in this block; bound them so they don't escape it.
        val savedNarrowings = context.saveNarrowings()
        try {
          val statements = Buffer[ActionStatement]()
          var foundTerminating = false
          for (elem <- elements) {
            if (foundTerminating) {
              // Report unreachable code
              bodyContext.warningReporter.setSourceFile(bodyContext.sourceFile)
              bodyContext.warningReporter.unreachableCode(elem.location)
            }
            val stmt = translate(elem, context)
            statements += stmt
            if (!foundTerminating && isTerminating(stmt)) {
              foundTerminating = true
            }
          }
          new StatementBlock(statements.toIndexedSeq*)
        } finally {
          context.restoreNarrowings(savedNarrowings)
        }
      }
    case node: AST.BreakExpression =>
      if (!context.inLoop) {
        bodyContext.report(BREAK_OUTSIDE_LOOP, node)
        new NOP(node.location)
      } else if (node.label != null && !context.hasLabel(node.label)) {
        bodyContext.report(LABEL_NOT_FOUND, node, node.label)
        new NOP(node.location)
      } else {
        new Break(node.location, node.label)
      }
    case node: AST.ContinueExpression =>
      if (!context.inLoop) {
        bodyContext.report(CONTINUE_OUTSIDE_LOOP, node)
        new NOP(node.location)
      } else if (node.label != null && !context.hasLabel(node.label)) {
        bodyContext.report(LABEL_NOT_FOUND, node, node.label)
        new NOP(node.location)
      } else {
        new Continue(node.location, node.label)
      }
    case node: AST.LabeledLoop =>
      context.openLabeledLoop(node.name) {
        attachLoopLabel(translate(node.loop, context), node.name)
      }
    case node: AST.ForeachExpression =>
      context.openScope {
        val collection = typed(node.collection, context).getOrElse(null)
        val arg = node.arg
        // A loop variable never reassigned in the body is effectively final, so
        // (like an unassigned parameter) it may be smart-cast by a null/is check;
        // otherwise it stays mutable and is not narrowed.
        val loopVarReassigned = AssignedVariableScanner.scan(node.statement).contains(arg.name)
        addForeachElement(arg, collection, context, isMutable = loopVarReassigned)
        var block = context.openLoop {
          translate(node.statement, context)
        }
        if (collection == null) {
          new NOP(node.location)
        } else if (collection.isBasicType || collection.isNullType) {
          bodyContext.report(INCOMPATIBLE_TYPE, node.collection, bodyContext.load("java.util.Collection"), collection.`type`)
          new NOP(node.location)
        } else {
          val elementVar = context.lookupOnlyCurrentScope(arg.name)
          if (elementVar == null) {
            // The element type failed to resolve (already reported)
            return new NOP(node.location)
          }
          val collectionVar = new ClosureLocalBinding(0, context.add(context.newName, collection.`type`), collection.`type`, isMutable = true)

          if (collection.isArrayType) {
            val counterVariable = new ClosureLocalBinding(0, context.add(context.newName, BasicType.INT), BasicType.INT, isMutable = true)
            val init =
              new StatementBlock(
                new ExpressionActionStatement(new SetLocal(collectionVar, collection)),
                new ExpressionActionStatement(new SetLocal(counterVariable, new IntValue(0)))
              )

            block =
              new ConditionalLoop(
                new BinaryTerm(LESS_THAN, BasicType.BOOLEAN, ref(counterVariable), new ArrayLength(ref(collectionVar))),
                new StatementBlock(
                  assign(elementVar, indexref(collectionVar, ref(counterVariable))),
                  block,
                  assign(counterVariable, new BinaryTerm(ADD, BasicType.INT, ref(counterVariable), new IntValue(1)))
                )
              )
            new StatementBlock(init, block)
          } else {
            val iteratorType = bodyContext.load("java.util.Iterator")
            val iteratorVar = new ClosureLocalBinding(0, context.add(context.newName, iteratorType), iteratorType, isMutable = true)
            val mIterator = findMethod(node.collection, collection.`type`.asInstanceOf[ObjectType], "iterator")
            val mNext = findMethod(node.collection, iteratorType, "next")
            val mHasNext = findMethod(node.collection, iteratorType, "hasNext")
            val init =
              new StatementBlock(
                new ExpressionActionStatement(new SetLocal(collectionVar, collection)),
                assign(iteratorVar, new Call(ref(collectionVar), mIterator, new Array[Term](0)))
              )
            var next: Term = new Call(ref(iteratorVar), mNext, new Array[Term](0))
            if (elementVar.tp != bodyContext.rootClass) {
              next = new AsInstanceOf(next, elementVar.tp)
            }
            block = new ConditionalLoop(new Call(ref(iteratorVar), mHasNext, new Array[Term](0)), new StatementBlock(assign(elementVar, next), block))
            new StatementBlock(init, block)
          }
        }
      }
    case node: AST.ForExpression =>
      context.openScope {
        val init = node.init match {
          case AST.ForInitDeclaration(declaration) => translate(declaration, context)
          case AST.ForInitExpression(expression) =>
            typed(expression, context).map(termToStatement(expression, _)).getOrElse(new NOP(node.location))
          case _: AST.ForInitEmpty => new NOP(node.location)
        }
        val condition = Option(node.condition).map { c =>
          val cond = ensureBooleanCondition(c, typed(c, context))
          if (cond != null) cond else new BoolValue(node.location, true)
        }.getOrElse(new BoolValue(node.location, true))
        val update = Option(node.update).flatMap(update => typed(update, context)).getOrElse(null)
        val loop = context.openLoop {
          val savedNarrowings = context.saveNarrowings()

          // #304: the for-body runs only when the condition is true, so narrow it
          // in the body exactly like the while-body (#303) and the if-then branch.
          // A `val`/parameter narrows outright (addNarrowing); a `var` narrows
          // flow-sensitively (addFlowNarrowing) — a reassignment in the body clears
          // it from that point on (AssignmentTyping calls clearFlowNarrowing). We
          // exclude a var reassigned in the CONDITION or the UPDATE: both re-run
          // each iteration and are not reliably non-null at body entry (the update
          // is the continue target and runs before the re-test). The narrowing is
          // bounded to the body by the save/restore, so it never leaks into the
          // update expression (typed above) or past the loop.
          if (node.condition != null) {
            val narrowing = body.extractNarrowing(node.condition, context)
            val condReassigned = AssignedVariableScanner.scan(node.condition)
            val updateReassigned =
              if (node.update != null) AssignedVariableScanner.scan(node.update) else Set.empty[String]
            narrowing.positive.foreach { case (name, tp) =>
              context.addNarrowing(name, tp)
            }
            narrowing.mutablePositive
              .filter { case (name, _) => !condReassigned.contains(name) && !updateReassigned.contains(name) }
              .foreach { case (name, tp) => context.addFlowNarrowing(name, tp) }
          }

          val result = translate(node.block, context)
          context.restoreNarrowings(savedNarrowings)
          result
        }
        // The update is the continue target, not part of the body: continue
        // must run it before re-testing the condition (otherwise a for-loop
        // continue skips the increment and loops forever)
        val updateStmt = if (update != null) new ExpressionActionStatement(update) else null
        new StatementBlock(init.location, init, new ConditionalLoop(node.location, condition, loop, isPostTest = false, label = null, update = updateStmt))
      }
    case node: AST.IfExpression =>
      context.openScope {
        val condition = ensureBooleanCondition(node.condition, typed(node.condition, context))

        // Smart cast: extract narrowing info and apply it to branches
        val narrowing = body.extractNarrowing(node.condition, context)
        val savedNarrowings = context.saveNarrowings()

        // Flow-sensitive narrowing for a reassignable `var` (issues #288/#289):
        // apply it inside a branch only when the var is not reassigned in the
        // condition or within that branch. A reassignment AFTER the branch does
        // not invalidate the narrowing inside it.
        val condReassigned = AssignedVariableScanner.scan(node.condition)
        def safeMutableNarrowings(m: Map[String, Type], branch: AST.BlockExpression): Map[String, Type] = {
          if (m.isEmpty) Map.empty
          else {
            val branchReassigned = AssignedVariableScanner.scan(branch)
            m.filter { case (name, _) => !condReassigned.contains(name) && !branchReassigned.contains(name) }
          }
        }

        // Apply positive narrowings for then-block
        narrowing.positive.foreach { case (name, tp) =>
          context.addNarrowing(name, tp)
        }
        safeMutableNarrowings(narrowing.mutablePositive, node.thenBlock).foreach { case (name, tp) =>
          context.addFlowNarrowing(name, tp)
        }
        val thenBlock = translate(node.thenBlock, context)
        context.restoreNarrowings(savedNarrowings)

        // Apply negative narrowings for else-block
        val elseBlock = if (node.elseBlock == null) null else {
          narrowing.negative.foreach { case (name, tp) =>
            context.addNarrowing(name, tp)
          }
          safeMutableNarrowings(narrowing.mutableNegative, node.elseBlock).foreach { case (name, tp) =>
            context.addFlowNarrowing(name, tp)
          }
          val result = translate(node.elseBlock, context)
          context.restoreNarrowings(savedNarrowings)
          result
        }

        // Guard-clause smart cast: when a branch always exits (return/throw/
        // break/continue), the opposite condition holds for the rest of the
        // enclosing block, so keep that narrowing in effect. The enclosing
        // block bounds it (it saves/restores narrowings around its elements).
        //   if x == null { return } ; x.foo()   // x narrowed to non-null
        //   if x != null { } else { return } ; x.foo()
        if (node.elseBlock == null) {
          if (isTerminating(thenBlock)) {
            narrowing.negative.foreach { case (name, tp) => context.addNarrowing(name, tp) }
          }
        } else if (isTerminating(elseBlock) && !isTerminating(thenBlock)) {
          narrowing.positive.foreach { case (name, tp) => context.addNarrowing(name, tp) }
        }

        if (condition != null) new IfStatement(condition, thenBlock, elseBlock) else new NOP(node.location)
      }
    case node: AST.LocalVariableDeclaration =>
      val binding = context.lookupOnlyCurrentScope(node.name)
      if (binding != null) {
        bodyContext.report(DUPLICATE_LOCAL_VARIABLE, node, node.name)
        return new NOP(node.location)
      }
      if (node.typeRef == null) {
        val inferred = typed(node.init, context).getOrElse(null)
        if (inferred == null) return new NOP(node.location)
        val inferredType = inferred.`type`
        if (inferredType == BasicType.VOID) {
          bodyContext.report(INCOMPATIBLE_TYPE, node.init, bodyContext.rootClass, inferredType)
          return new NOP(node.location)
        }
        typing.checkAndReportShadowing(node.name, node.location, context)
        // A `var` never reassigned in its enclosing body is effectively final,
        // so it can be smart-cast like a `val` (issue #273). `val` (final) is
        // always immutable regardless.
        val mutable = !Modifier.isFinal(node.modifiers) && context.isReassigned(node.name)
        val index = context.add(node.name, inferredType, isMutable = mutable)
        context.recordDeclaration(node.name, node.location)
        new ExpressionActionStatement(new SetLocal(node.location, 0, index, inferredType, inferred))
      } else {
        val lhsTypeOpt = mapFrom(node.typeRef)
        if (lhsTypeOpt.isEmpty) {
          // The declared type failed to resolve (E0003 already reported).
          // Register the binding anyway — at the initializer's type when it can
          // be typed, otherwise Object — so later references to this variable
          // resolve instead of cascading a spurious E0002 through the rest of
          // the block (error recovery, cf. issue #257).
          val recoveryType =
            (if (node.init != null) typed(node.init, context).map(_.`type`) else None)
              .filter(t => t != null && t != BasicType.VOID && !t.isBottomType)
              .getOrElse(bodyContext.rootClass)
          typing.checkAndReportShadowing(node.name, node.location, context)
          val mutable = !Modifier.isFinal(node.modifiers) && context.isReassigned(node.name)
          context.add(node.name, recoveryType, isMutable = mutable)
          context.recordDeclaration(node.name, node.location)
          return new NOP(node.location)
        }
        val lhsType = lhsTypeOpt.get
        // A local `val` without an initializer can never be usefully assigned
        // (reassignment of a val is E0036), so reading it would silently yield
        // the JVM default (null/0) or an NPE. Reject it up front (issue #280).
        // Top-level `val` declarations become static fields and set
        // `allowUninitializedLocal`, so they keep their field-init behavior.
        if (node.init == null && Modifier.isFinal(node.modifiers) && !context.allowUninitializedLocal) {
          bodyContext.report(VAL_REQUIRES_INITIALIZER, node, node.name)
          typing.checkAndReportShadowing(node.name, node.location, context)
          context.add(node.name, lhsType, isMutable = false)
          context.recordDeclaration(node.name, node.location)
          return new NOP(node.location)
        }
        // Type the initializer BEFORE the variable is in scope, so `val x: T = x`
        // is a clean "variable not found" error rather than loading an
        // uninitialized slot (VerifyError). Matches the no-annotation branch.
        //
        // When the initializer itself is a type error (E0000) or otherwise fails
        // to type, register the binding at its DECLARED type anyway, so later
        // references to this variable resolve instead of cascading a spurious
        // E0002 through the rest of the block (issue #257). A placeholder default
        // value stands in for codegen, which never runs once an error is
        // reported.
        val (typedValue, failed): (Term, Boolean) =
          if (node.init != null) {
            typed(node.init, context, lhsType) match {
              case None => (defaultValue(lhsType), true)
              case Some(v) =>
                val value = processAssignable(node.init, lhsType, v)
                if (value == null) (defaultValue(lhsType), true) else (value, false)
            }
          } else (defaultValue(lhsType), false)
        typing.checkAndReportShadowing(node.name, node.location, context)
        // A `var` never reassigned in its enclosing body is effectively final,
        // so it can be smart-cast like a `val` (issue #273).
        val mutable = !Modifier.isFinal(node.modifiers) && context.isReassigned(node.name)
        val index = context.add(node.name, lhsType, isMutable = mutable)
        context.recordDeclaration(node.name, node.location)
        if (failed) new NOP(node.location)
        else new ExpressionActionStatement(new SetLocal(node.location, 0, index, lhsType, typedValue))
      }
    case node: AST.DestructuringDeclaration =>
      translateDestructuring(node, context)
    case node: AST.ReturnExpression =>
      val returnType = context.returnType
      if (context.collectingReturnTypes) {
        if (node.result == null) {
          context.collectReturnType(BasicType.VOID)
          new Return(node.location, null)
        } else {
          typed(node.result, context) match {
            case None =>
              new Return(node.location, null)
            case Some(returned) =>
              context.collectReturnType(returned.`type`)
              new Return(node.location, returned)
          }
        }
      } else if (node.result == null) {
        val expected = BasicType.VOID
        if (returnType != expected) bodyContext.report(CANNOT_RETURN_VALUE, node)
        new Return(node.location, null)
      } else {
        typed(node.result, context, returnType) match {
          case None =>
            new Return(node.location, null)
          case Some(returned) if returned.`type` == BasicType.VOID =>
            bodyContext.report(CANNOT_RETURN_VALUE, node)
            new Return(node.location, null)
          case Some(returned) =>
            val value = processAssignable(node.result, returnType, returned)
            if (value == null) new Return(node.location, null) else new Return(node.location, value)
        }
      }
    case node: AST.SelectExpression =>
      // Delegate to ControlExpressionTyping which handles all pattern types.
      // asStatement = true: the select's value is unused here, so branches need
      // not unify (mixed value/void is fine), mirroring if/else in statement
      // position (issue #297). Wrapping the whole select term (rather than
      // unwrapping to its inner statement) preserves its BOTTOM type when every
      // branch terminates, so definitelyReturns still recognizes it (E0067).
      body.controlExpressionTyping.typeSelectExpression(node, context, asStatement = true).map { e =>
        typing.put(node, e)
        new ExpressionActionStatement(node.location, e)
      }.getOrElse(new NOP(node.location))
    case node: AST.SynchronizedExpression =>
      // SynchronizedExpression is now handled as an expression (SynchronizedTerm)
      // This case converts it to a statement for contexts that need ActionStatement
      context.openScope {
        val lock = typed(node.condition, context).getOrElse(null)
        if (lock != null && lock.isBasicType) {
          bodyContext.report(INCOMPATIBLE_TYPE, node.condition, bodyContext.load("java.lang.Object"), lock.`type`)
          new NOP(node.location)
        } else if (lock == null) {
          new NOP(node.location)
        } else {
          val block = translate(node.block, context)
          new Synchronized(node.location, lock, block)
        }
      }
    case node: AST.ThrowExpression =>
      val expressionOpt = typed(node.target, context)
      for (expression <- expressionOpt) {
        val expected = bodyContext.load("java.lang.Throwable")
        val detected = expression.`type`
        if (!TypeRules.isSuperType(expected, detected)) {
          bodyContext.report(INCOMPATIBLE_TYPE, node, expected, detected)
        }
      }
      new Throw(node.location, expressionOpt.getOrElse(null))
    case node: AST.TryExpression =>
      // リソースを処理（try-with-resources）
      val resourceBindings = scala.collection.mutable.ArrayBuffer[(ClosureLocalBinding, Term)]()
      val autoCloseable = bodyContext.load("java.lang.AutoCloseable")

      context.openScope {
        for (resource <- node.resources) {
          val initOpt = typed(resource.init, context)
          initOpt.foreach { init =>
            val resourceTypeOpt =
              if (resource.typeRef != null) mapFrom(resource.typeRef)
              else Some(init.`type`)

            resourceTypeOpt.foreach { resourceType =>
              // Always add the variable to scope so it can be referenced in try block
              val index = context.add(resource.name, resourceType, isMutable = false)
              val binding = new ClosureLocalBinding(0, index, resourceType, isMutable = false)

              if (TypeRules.isSuperType(autoCloseable, resourceType)) {
                resourceBindings += ((binding, init))
              } else {
                // Report error but still allow the variable to be used
                bodyContext.report(INCOMPATIBLE_TYPE, resource, autoCloseable, resourceType)
              }
            }
          }
        }

        val tryStatement = translate(node.tryBlock, context)
        val binds = new Array[ClosureLocalBinding](node.recClauses.length)
        val catchBlocks = new Array[ActionStatement](node.recClauses.length)
        for (i <- 0 until node.recClauses.length) {
          val (argument, body) = node.recClauses(i)
          context.openScope {
            val argType = addArgument(argument, context)
            val expected = bodyContext.load("java.lang.Throwable")
            if (!TypeRules.isSuperType(expected, argType)) {
              bodyContext.report(INCOMPATIBLE_TYPE, argument, expected, argType)
            }
            binds(i) = context.lookupOnlyCurrentScope(argument.name)
            catchBlocks(i) = translate(body, context)
          }
        }
        // Handle finally block
        val finallyStatement = if (node.finBlock != null) translate(node.finBlock, context) else null
        new Try(node.location, resourceBindings.toArray, tryStatement, binds, catchBlocks, finallyStatement)
      }
    case node: AST.WhileExpression =>
      context.openScope {
        val condition = ensureBooleanCondition(node.condition, typed(node.condition, context))
        val savedNarrowings = context.saveNarrowings()

        // #303: the body runs only when the condition is true, so narrow it in
        // the body exactly like the `if`-then branch does (same condition→body
        // narrowing). A `val`/parameter narrows outright (addNarrowing); a `var`
        // narrows flow-sensitively (addFlowNarrowing), which is FLOW-SENSITIVE:
        //   while cur != null { use(cur); cur = cur.next() }
        // the use before the reassignment sees the non-null narrowing and the
        // reassignment clears it from that point on (AssignmentTyping calls
        // clearFlowNarrowing), so a use AFTER the reassignment correctly fails.
        // We deliberately do NOT pre-filter on "reassigned anywhere in the body"
        // — that would wrongly drop the narrowing for the linked-list pattern,
        // whose whole point is the `cur = cur.next()` advance. The #288
        // closure-escape safety is preserved separately: typing a closure body
        // runs under withoutFlowNarrowings, so a flow-narrowed var captured by a
        // closure in the body is not narrowed inside the closure. We only exclude
        // a var reassigned in the CONDITION itself (it is not reliably non-null
        // at body entry), matching the if/&& handling of #288/#289/#294.
        val narrowing = body.extractNarrowing(node.condition, context)
        val condReassigned = AssignedVariableScanner.scan(node.condition)
        narrowing.positive.foreach { case (name, tp) =>
          context.addNarrowing(name, tp)
        }
        narrowing.mutablePositive
          .filter { case (name, _) => !condReassigned.contains(name) }
          .foreach { case (name, tp) => context.addFlowNarrowing(name, tp) }

        // #289: `while ((v = e) != null) { body }` assigns v then checks it is
        // non-null, so v is non-null at the top of every iteration. Narrow v
        // flow-sensitively at the start of the body (dropped if the body
        // reassigns v). Bounded by this while's scope.
        assignInConditionNonNullTarget(node.condition, context).foreach { case (name, tp) =>
          context.addFlowNarrowing(name, tp)
        }
        val thenBlock = context.openLoop {
          translate(node.block, context)
        }
        context.restoreNarrowings(savedNarrowings)
        if (condition == null) new NOP(node.location)
        else new ConditionalLoop(node.location, condition, thenBlock)
      }
    case node: AST.DoWhileExpression =>
      context.openScope {
        val body = context.openLoop {
          translate(node.block, context)
        }
        val condition = ensureBooleanCondition(node.condition, typed(node.condition, context))
        if (condition == null) new NOP(node.location)
        else new ConditionalLoop(node.location, condition, body, isPostTest = true)
      }
    case node: AST.Expression =>
      typed(node, context).map(termToStatement(node, _)).getOrElse(new NOP(node.location))
  }

  /**
   * Recognizes an assign-in-condition null test `(v = e) != null` (or the
   * mirror `null != (v = e)`) whose target `v` is a mutable nullable local, and
   * returns `(v, nonNullType)` so the loop body can narrow it (issue #289).
   * Returns None for any other shape.
   */
  private def assignInConditionNonNullTarget(
    condition: AST.Expression, context: LocalContext
  ): Option[(String, Type)] = {
    def targetOf(e: AST.Expression): Option[String] = e match {
      case AST.Assignment(_, id: AST.Id, _) => Some(id.name)
      case _ => None
    }
    val nameOpt = condition match {
      case AST.NotEqual(_, lhs, AST.NullLiteral(_)) => targetOf(lhs)
      case AST.NotEqual(_, AST.NullLiteral(_), rhs) => targetOf(rhs)
      case _ => None
    }
    nameOpt.flatMap { name =>
      val binding = context.lookup(name)
      if (binding == null || !binding.isMutable) None
      else binding.tp match {
        case n: NullableType => Some((name, n.innerType))
        case tv: TypeVariableType if tv.nullability == Nullability.Nullable => Some((name, tv.nonNullView))
        case _ => None
      }
    }
  }

  private def typed(node: AST.Expression, context: LocalContext, expected: Type = null): Option[Term] =
    body.typed(node, context, expected)

  private def addArgument(arg: AST.Argument, context: LocalContext): Type =
    body.addArgument(arg, context)

  /**
   * Binds a foreach element variable, refining a bare-generic annotation to
   * the collection's actual element type. `foreach (k, v) in map` desugars to
   * `foreach __e: Map$Entry in map.entrySet() { val k = __e.getKey(); ... }`,
   * but the desugar can only name the raw `Map$Entry` at parse time, so its
   * getKey/getValue return the unsubstituted type variables. Recovering the
   * element type from the collection (`Set[Map.Entry[K, V]]`) restores K and V.
   */
  private def addForeachElement(arg: AST.Argument, collection: Term, context: LocalContext, isMutable: Boolean = true): Unit = {
    if (context.lookupOnlyCurrentScope(arg.name) != null) {
      bodyContext.report(DUPLICATE_LOCAL_VARIABLE, arg, arg.name)
    } else typing.mapFrom(arg.typeRef) match { // raw-exempt: `foreach (k,v)` desugars to a raw Map$Entry placeholder that refineElementType restores
      case Some(annotated) =>
        val elementType = if (collection == null) annotated else refineElementType(annotated, collection.`type`)
        context.add(arg.name, elementType, isMutable)
      case None => ()
    }
  }

  /**
   * Refine a raw-generic element annotation to the collection's element type
   * when they are the same class applied with arguments. Conservative: leaves
   * explicit applied annotations and differing classes untouched, so ordinary
   * `foreach x: T in coll` is unaffected.
   */
  private def refineElementType(annotated: Type, collectionType: Type): Type = annotated match {
    case rawAnno: ClassType if !rawAnno.isInstanceOf[AppliedClassType] =>
      iterableElementType(collectionType) match {
        case Some(applied: AppliedClassType) if applied.raw.name == rawAnno.name => applied
        case _ => annotated
      }
    case _ => annotated
  }

  /** The element type of a collection, via its java.lang.Iterable view. */
  private def iterableElementType(tp: Type): Option[Type] = tp match {
    case ct: ClassType =>
      AppliedTypeViews.collectAppliedViewsFrom(ct).collectFirst {
        case (raw, applied) if raw.name == "java.lang.Iterable" && applied.typeArguments.nonEmpty =>
          applied.typeArguments.head
      }
    case _ => None
  }

  private def processAssignable(node: AST.Node, expected: Type, term: Term): Term =
    body.processAssignable(node, expected, term)

  private def defaultValue(typeRef: Type): Term =
    body.defaultValue(typeRef)

  // Local declaration positions (local var / try-resource / foreach element
  // annotations) forbid raw generic types.
  private def mapFrom(typeNode: AST.TypeNode): Option[Type] =
    typing.mapFromDeclared(typeNode)

  private def processNodes(nodes: Array[AST.Expression], typeRef: Type, bind: ClosureLocalBinding, context: LocalContext): Term =
    body.processNodes(nodes, typeRef, bind, context)

  private def findMethod(node: AST.Node, target: ObjectType, name: String): Method =
    body.findMethod(node, target, name)

  private def indexref(bind: ClosureLocalBinding, value: Term): Term =
    new RefArray(new RefLocal(bind), value)

  private def assign(bind: ClosureLocalBinding, value: Term): ActionStatement =
    new ExpressionActionStatement(new SetLocal(bind, value))

  private def ref(bind: ClosureLocalBinding): Term =
    new RefLocal(bind)

  /**
   * Attaches a label to the loop produced by translating a labeled
   * statement. for/foreach lower to a block whose last statement is the
   * loop, so the search recurses into trailing positions.
   */
  private def attachLoopLabel(stmt: ActionStatement, name: String): ActionStatement = stmt match {
    case loop: ConditionalLoop =>
      new ConditionalLoop(loop.location, loop.condition, loop.stmt, loop.isPostTest, name, loop.update)
    case block: StatementBlock if block.statements.nonEmpty =>
      val relabeled = block.statements.dropRight(1) :+ attachLoopLabel(block.statements.last, name)
      new StatementBlock(relabeled.toIndexedSeq*)
    case other => other
  }

  private def termToStatement(node: AST.Node, term: Term): ActionStatement = term match {
    case stmtTerm: StatementTerm => stmtTerm.statement
    case _ => new ExpressionActionStatement(node.location, term)
  }

  /**
   * val (a, b) = expr — evaluates expr once into a synthetic local, then
   * binds each name to the corresponding positional component (record
   * components in declaration order, or getKey/getValue for Map.Entry).
   */
  private def translateDestructuring(node: AST.DestructuringDeclaration, context: LocalContext): ActionStatement = {
    val initTermOpt = typed(node.init, context)
    if (initTermOpt.isEmpty) return new NOP(node.location)
    val initTerm = initTermOpt.get
    val initType = initTerm.`type`

    def zeroArg(ct: ClassType, name: String): Option[Method] =
      ct.methods(name).find(m => m.arguments.isEmpty && !Modifier.isStatic(m.modifier))

    def accessors(tp: Type): Option[Seq[(Method, Type)]] = tp match {
      case ct: ClassType =>
        val raw = ct match {
          case a: AppliedClassType => a.raw
          case c => c
        }
        raw match {
          case d: ClassDefinition if d.recordComponents.isDefined =>
            val comps = d.recordComponents.get.toSeq
            val resolved = comps.flatMap { case (compName, _) =>
              zeroArg(ct, compName).map(m => (m, TypeSubst.withClassOnly(m.returnType, ct)))
            }
            if (resolved.length == comps.length) Some(resolved) else None
          case _ =>
            bodyContext.table.load("java.util.Map$Entry") match {
              case Some(entryType) if TypeRules.isSuperType(entryType, ct) =>
                for {
                  k <- zeroArg(ct, "getKey")
                  v <- zeroArg(ct, "getValue")
                } yield Seq((k, TypeSubst.withClassOnly(k.returnType, ct)), (v, TypeSubst.withClassOnly(v.returnType, ct)))
              case _ => None
            }
        }
      case _ => None
    }

    accessors(initType) match {
      case None =>
        bodyContext.report(NOT_A_RECORD_TYPE, node.init, initType.displayName)
        new NOP(node.location)
      case Some(components) =>
        if (components.length != node.names.length) {
          bodyContext.report(WRONG_BINDING_COUNT, node, Int.box(components.length), Int.box(node.names.length), initType.displayName)
          return new NOP(node.location)
        }
        val statements = Buffer[ActionStatement]()
        val tmpIndex = context.add(context.newName, initType, isMutable = false)
        statements += new ExpressionActionStatement(new SetLocal(node.location, 0, tmpIndex, initType, initTerm))
        for ((name, (method, compType)) <- node.names.zip(components)) {
          if (context.lookupOnlyCurrentScope(name) != null) {
            bodyContext.report(DUPLICATE_LOCAL_VARIABLE, node, name)
            return new NOP(node.location)
          }
          typing.checkAndReportShadowing(name, node.location, context)
          val index = context.add(name, compType, isMutable = !Modifier.isFinal(node.modifiers))
          context.recordDeclaration(name, node.location)
          val call = new Call(new RefLocal(0, tmpIndex, initType), method, Array.empty[Term])
          val value = TypeSubst.withCast(call, compType)
          statements += new ExpressionActionStatement(new SetLocal(node.location, 0, index, compType, value))
        }
        new StatementBlock(statements.toIndexedSeq*)
    }
  }
}
