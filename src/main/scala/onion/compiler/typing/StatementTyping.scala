package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.TypedAST.BinaryTerm.Constants.*
import onion.compiler.toolbox.Boxing

import scala.collection.mutable.Buffer

final class StatementTyping(private val typing: Typing, private val body: TypingBodyPass) {
  import typing.*

  /** Ensures the term is boolean, unboxing if needed. Returns the term (possibly unboxed) or null on error. */
  private def ensureBooleanCondition(node: AST.Node, termOpt: Option[Term]): Term = {
    termOpt match {
      case None => null
      case Some(term) =>
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
  }

  def translate(node: AST.CompoundExpression, context: LocalContext): ActionStatement = node match {
    case AST.BlockExpression(_, elements) =>
      context.openScope {
        new StatementBlock(elements.map(e => translate(e, context)).toIndexedSeq*)
      }
    case node: AST.BreakExpression =>
      new Break(node.location)
    case node: AST.ContinueExpression =>
      new Continue(node.location)
    case node: AST.EmptyExpression =>
      new NOP(node.location)
    case node@AST.ExpressionBox(_, body) =>
      typed(body, context).map { e => new ExpressionActionStatement(node.location, e) }.getOrElse(new NOP(node.location))
    case node: AST.ForeachExpression =>
      context.openScope {
        val collection = typed(node.collection, context).getOrElse(null)
        val arg = node.arg
        addArgument(arg, context)
        var block = translate(node.statement, context)
        if (collection == null) {
          new NOP(node.location)
        } else if (collection.isBasicType) {
          report(INCOMPATIBLE_TYPE, node.collection, load("java.util.Collection"), collection.`type`)
          new NOP(node.location)
        } else {
          val elementVar = context.lookupOnlyCurrentScope(arg.name)
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
            val iteratorType = load("java.util.Iterator")
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
            if (elementVar.tp != rootClass) {
              next = new AsInstanceOf(next, elementVar.tp)
            }
            block = new ConditionalLoop(new Call(ref(iteratorVar), mHasNext, new Array[Term](0)), new StatementBlock(assign(elementVar, next), block))
            new StatementBlock(init, block)
          }
        }
      }
    case node: AST.ForExpression =>
      context.openScope {
        val init = Option(node.init).map(init => translate(init, context)).getOrElse(new NOP(node.location))
        val condition = Option(node.condition).map { c =>
          val cond = ensureBooleanCondition(c, typed(c, context))
          if (cond != null) cond else new BoolValue(node.location, true)
        }.getOrElse(new BoolValue(node.location, true))
        val update = Option(node.update).flatMap(update => typed(update, context)).getOrElse(null)
        var loop = translate(node.block, context)
        if (update != null) loop = new StatementBlock(loop, new ExpressionActionStatement(update))
        new StatementBlock(init.location, init, new ConditionalLoop(condition, loop))
      }
    case node: AST.IfExpression =>
      context.openScope {
        val condition = ensureBooleanCondition(node.condition, typed(node.condition, context))
        val thenBlock = translate(node.thenBlock, context)
        val elseBlock = if (node.elseBlock == null) null else translate(node.elseBlock, context)
        if (condition != null) new IfStatement(condition, thenBlock, elseBlock) else new NOP(node.location)
      }
    case node: AST.LocalVariableDeclaration =>
      val binding = context.lookupOnlyCurrentScope(node.name)
      if (binding != null) {
        report(DUPLICATE_LOCAL_VARIABLE, node, node.name)
        return new NOP(node.location)
      }
      if (node.typeRef == null) {
        val inferred = typed(node.init, context).getOrElse(null)
        if (inferred == null) return new NOP(node.location)
        val inferredType = inferred.`type`
        if (inferredType == BasicType.VOID) {
          report(INCOMPATIBLE_TYPE, node.init, rootClass, inferredType)
          return new NOP(node.location)
        }
        val index = context.add(node.name, inferredType, isMutable = !Modifier.isFinal(node.modifiers))
        new ExpressionActionStatement(new SetLocal(node.location, 0, index, inferredType, inferred))
      } else {
        val lhsType = mapFrom(node.typeRef)
        if (lhsType == null) return new NOP(node.location)
        val index = context.add(node.name, lhsType, isMutable = !Modifier.isFinal(node.modifiers))
        var local: SetLocal = null
        if (node.init != null) {
          val valueNode = typed(node.init, context, lhsType)
          valueNode match {
            case None => return new NOP(node.location)
            case Some(v) =>
              val value = processAssignable(node.init, lhsType, v)
              if (value == null) return new NOP(node.location)
              local = new SetLocal(node.location, 0, index, lhsType, value)
          }
        } else {
          local = new SetLocal(node.location, 0, index, lhsType, defaultValue(lhsType))
        }
        new ExpressionActionStatement(local)
      }
    case node: AST.ReturnExpression =>
      val returnType = context.returnType
      if (node.result == null) {
        val expected = BasicType.VOID
        if (returnType != expected) report(CANNOT_RETURN_VALUE, node)
        new Return(node.location, null)
      } else {
        typed(node.result, context, returnType) match {
          case None =>
            new Return(node.location, null)
          case Some(returned) if returned.`type` == BasicType.VOID =>
            report(CANNOT_RETURN_VALUE, node)
            new Return(node.location, null)
          case Some(returned) =>
            val value = processAssignable(node.result, returnType, returned)
            if (value == null) new Return(node.location, null) else new Return(node.location, value)
        }
      }
    case node: AST.SelectExpression =>
      val conditionOpt = typed(node.condition, context)
      if (conditionOpt.isEmpty) return new NOP(node.location)
      val condition = conditionOpt.get
      val name = context.newName
      val index = context.add(name, condition.`type`)
      val statement = if (node.cases.length == 0) {
        Option(node.elseBlock).map(e => translate(e, context)).getOrElse(new NOP(node.location))
      } else {
        val cases = node.cases
        val nodes = Buffer[Term]()
        val thens = Buffer[ActionStatement]()
        for ((patterns, thenClause) <- cases) {
          val bind = context.lookup(name)
          // Extract expressions from patterns (for statement context, only ExpressionPattern is supported)
          val expressions = patterns.collect { case AST.ExpressionPattern(e) => e }
          if (expressions.length != patterns.length) {
            report(UNIMPLEMENTED_FEATURE, node)
          }
          nodes += processNodes(expressions.toArray, condition.`type`, bind, context)
          thens += translate(thenClause, context)
        }
        var branches: ActionStatement = if (node.elseBlock != null) {
          translate(node.elseBlock, context)
        } else {
          null
        }
        for (i <- (cases.length - 1) to (0, -1)) {
          branches = new IfStatement(nodes(i), thens(i), branches)
        }
        branches
      }
      new StatementBlock(condition.location, new ExpressionActionStatement(condition.location, new SetLocal(0, index, condition.`type`, condition)), statement)
    case node: AST.SynchronizedExpression =>
      // SynchronizedExpression is now handled as an expression (SynchronizedTerm)
      // This case converts it to a statement for contexts that need ActionStatement
      context.openScope {
        val lock = typed(node.condition, context).getOrElse(null)
        if (lock != null && lock.isBasicType) {
          report(INCOMPATIBLE_TYPE, node.condition, load("java.lang.Object"), lock.`type`)
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
        val expected = load("java.lang.Throwable")
        val detected = expression.`type`
        if (!TypeRules.isSuperType(expected, detected)) {
          report(INCOMPATIBLE_TYPE, node, expected, detected)
        }
      }
      new Throw(node.location, expressionOpt.getOrElse(null))
    case node: AST.TryExpression =>
      // リソースを処理（try-with-resources）
      val resourceBindings = scala.collection.mutable.ArrayBuffer[(ClosureLocalBinding, Term)]()
      val autoCloseable = load("java.lang.AutoCloseable")

      context.openScope {
        for (resource <- node.resources) {
          val initOpt = typed(resource.init, context)
          initOpt.foreach { init =>
            val resourceType =
              if (resource.typeRef != null) mapFrom(resource.typeRef)
              else init.`type`

            if (resourceType != null) {
              // Always add the variable to scope so it can be referenced in try block
              val index = context.add(resource.name, resourceType, isMutable = false)
              val binding = new ClosureLocalBinding(0, index, resourceType, isMutable = false)

              if (TypeRules.isSuperType(autoCloseable, resourceType)) {
                resourceBindings += ((binding, init))
              } else {
                // Report error but still allow the variable to be used
                report(INCOMPATIBLE_TYPE, resource, autoCloseable, resourceType)
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
            val expected = load("java.lang.Throwable")
            if (!TypeRules.isSuperType(expected, argType)) {
              report(INCOMPATIBLE_TYPE, argument, expected, argType)
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
        val thenBlock = translate(node.block, context)
        new ConditionalLoop(node.location, condition, thenBlock)
      }
  }

  private def typed(node: AST.Expression, context: LocalContext, expected: Type = null): Option[Term] =
    body.typed(node, context, expected)

  private def addArgument(arg: AST.Argument, context: LocalContext): Type =
    body.addArgument(arg, context)

  private def processAssignable(node: AST.Node, expected: Type, term: Term): Term =
    body.processAssignable(node, expected, term)

  private def defaultValue(typeRef: Type): Term =
    body.defaultValue(typeRef)

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
}
