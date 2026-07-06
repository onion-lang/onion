package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.typing.session.TypingBodyContext

import scala.util.boundary, boundary.break

/**
 * Types `try` expressions (including try-with-resources and finally blocks)
 * and `synchronized` expressions.
 */
final class TryExpressionTyping(
  private val typing: Typing,
  private val bodyContext: TypingBodyContext,
  private val body: TypingBodyPass,
  private val control: ControlExpressionTyping
) {

  def typeTryExpression(node: AST.TryExpression, context: LocalContext, expected: Type = null): Option[Term] = boundary {
    // Thread the expected result type into the try body and each catch body so an
    // under-determined empty collection literal there (`try { [] } catch e { [1] }`)
    // target-types its element type from the expected type, mirroring if/else,
    // select, and argument/block-trailing positions (issue #301). The finally block
    // does NOT participate in the value (its result is discarded), so it is typed
    // without an expected type. A null expected leaves branch typing unchanged.
    val branchExpected: Type =
      if (expected != null && !expected.isBottomType && expected != BasicType.VOID) expected else null
    // リソースを処理（try-with-resources）
    val resourceBindings = scala.collection.mutable.ArrayBuffer[(ClosureLocalBinding, Term)]()
    val autoCloseable = bodyContext.load("java.lang.AutoCloseable")
    var resourceFailed = false

    context.openScope {
      for (resource <- node.resources) {
        val initOpt = typed(resource.init, context)
        initOpt match {
          case None =>
            resourceFailed = true
          case Some(init) =>
            val resourceTypeOpt =
              if (resource.typeRef != null) typing.mapFrom(resource.typeRef)
              else Some(init.`type`)

            resourceTypeOpt match {
              case Some(resourceType) =>
                // Always add the variable to scope so it can be referenced in try block
                val index = context.add(resource.name, resourceType, isMutable = false)
                val binding = new ClosureLocalBinding(0, index, resourceType, isMutable = false)

                // AutoCloseableを実装しているか確認
                if (!TypeRules.isSuperType(autoCloseable, resourceType)) {
                  bodyContext.report(INCOMPATIBLE_TYPE, resource, autoCloseable, resourceType)
                  resourceFailed = true
                } else {
                  resourceBindings += ((binding, init))
                }
              case None =>
                resourceFailed = true
            }
        }
      }

      if (resourceFailed) break(None)

      val tryTermOpt = typeBlockExpression(node.tryBlock, context, branchExpected)
      val tryTerm = tryTermOpt.getOrElse(null)
      if (tryTerm == null) break(None)

      val binds = new Array[ClosureLocalBinding](node.recClauses.length)
      val catchTerms = new Array[Term](node.recClauses.length)
      var failed = false
      for (i <- 0 until node.recClauses.length) {
        val (argument, catchBody) = node.recClauses(i)
        context.openScope {
          val argType = body.addArgument(argument, context)
          val expected = bodyContext.load("java.lang.Throwable")
          if (!TypeRules.isSuperType(expected, argType)) {
            bodyContext.report(INCOMPATIBLE_TYPE, argument, expected, argType)
          }
          binds(i) = context.lookupOnlyCurrentScope(argument.name)
          typeBlockExpression(catchBody, context, branchExpected) match {
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
          control.foldLub(node, types).orNull
        }
      if (resultType == null) break(None)

      val resultVar =
        if (resultType.isBottomType || resultType == BasicType.VOID) null
        else new ClosureLocalBinding(0, context.add(context.newName, resultType, isMutable = true), resultType, isMutable = true)

      val tryStmt =
        if (resultVar == null) control.termToStatement(node.tryBlock, tryTerm)
        else control.assignBranch(node.tryBlock, tryTerm, resultVar, resultType)

      val catchStatements = new Array[ActionStatement](catchTerms.length)
      for (i <- catchTerms.indices) {
        val term = catchTerms(i)
        val stmt =
          if (resultVar == null) control.termToStatement(node.recClauses(i)._2, term)
          else control.assignBranch(node.recClauses(i)._2, term, resultVar, resultType)
        catchStatements(i) = stmt
      }

      // Type check finally block if present
      val finallyStmt: ActionStatement =
        if (node.finBlock != null) {
          typeBlockExpression(node.finBlock, context) match {
            case Some(finallyTerm) =>
              control.termToStatement(node.finBlock, finallyTerm)
            case None =>
              null
          }
        } else {
          null
        }

      val statement = new Try(node.location, resourceBindings.toArray, tryStmt, binds, catchStatements, finallyStmt)
      if (resultVar == null) {
        Some(control.statementTerm(statement, resultType, node.location))
      } else {
        Some(new Begin(node.location, Array[Term](control.statementTerm(statement, BasicType.VOID, node.location), new RefLocal(resultVar))))
      }
    }
  }

  def typeSynchronizedExpression(node: AST.SynchronizedExpression, context: LocalContext): Option[Term] = boundary {
    context.openScope {
      // Type the lock expression
      val lock = typed(node.condition, context).getOrElse(break(None))

      // Lock must be object type, not primitive
      if (lock.isBasicType) {
        bodyContext.report(INCOMPATIBLE_TYPE, node.condition, bodyContext.load("java.lang.Object"), lock.`type`)
        break(None)
      }

      // Type the body as an expression (returns value)
      val bodyTerm = typeBlockExpression(node.block, context).getOrElse(break(None))

      // Create SynchronizedTerm that returns the body's value
      Some(new SynchronizedTerm(node.location, lock, bodyTerm))
    }
  }

  private def typed(node: AST.Expression, context: LocalContext): Option[Term] =
    body.typed(node, context)

  private def typeBlockExpression(node: AST.BlockExpression, context: LocalContext, expected: Type = null): Option[Term] =
    control.typeBlockExpression(node, context, expected)
}
