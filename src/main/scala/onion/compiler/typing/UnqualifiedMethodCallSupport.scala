package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*
import onion.compiler.typing.session.TypingBodyContext

import java.util.{TreeSet => JTreeSet}

private[compiler] final class UnqualifiedMethodCallSupport(
  bodyContext: TypingBodyContext,
  calls: MethodCallTyping
) {
  private val overloadSupport = new CallOverloadSupport(calls.typing, calls)
  private val callableValueCallSupport = new CallableValueCallSupport(bodyContext, calls)
  private val staticImportMethodCallSupport = new StaticImportMethodCallSupport(bodyContext, calls)

  def typeUnqualifiedMethodCall(
    node: AST.UnqualifiedMethodCall,
    context: LocalContext,
    expected: Type = null
  ): Option[Term] = {
    if (ArgumentHelpers.hasNamedArguments(node.args)) {
      return typeUnqualifiedMethodCallWithNamedArgs(node, context, expected)
    }

    val params = calls.typedTerms(node.args.toArray, context)
    if (params == null) return None
    val targetType = bodyContext.definition
    val methods = MethodResolution.findMethods(targetType, node.name, params, bodyContext.table)
    if (methods.length == 0) {
      staticImportMethodCallSupport.resolveStaticImportMethodCall(node, params, expected) match {
        case MethodFallbackLookup.Found(term) =>
          Some(term)
        case MethodFallbackLookup.Error =>
          None
        case MethodFallbackLookup.NotFound =>
          callableValueCallSupport.resolveCallableValue(node, params, context, expected) match {
            case Some(term) =>
              Some(term)
            case None =>
              calls.reportMethodNotFound(node, targetType, node.name, calls.types(params))
              None
          }
      }
    } else if (methods.length > 1) {
      calls.reportAmbiguousMethods(node, node.name, methods)
      None
    } else {
      val method = methods(0)
      if (!ensureInstanceAvailable(node, method, context)) return None
      val classSubst: scala.collection.immutable.Map[String, Type] = scala.collection.immutable.Map.empty
      calls.buildResolvedCall(node, method, params, node.typeArgs, classSubst, expected)(
        expectedArgs => calls.prepareCallParams(node, node.args, method, params, expectedArgs),
        finalParams => rawUnqualifiedCall(targetType, method, finalParams, context)
      )
    }
  }

  /**
   * An unqualified call to an instance method needs 'this'; in a static
   * context that's an error (fuzz: bare toString() inside static main used
   * to crash codegen with "no 'this' pointer within static method").
   */
  private def ensureInstanceAvailable(node: AST.Node, method: Method, context: LocalContext): Boolean = {
    if ((method.modifier & AST.M_STATIC) == 0 && context.isStatic) {
      bodyContext.report(SemanticError.CURRENT_INSTANCE_NOT_AVAILABLE, node)
      false
    } else true
  }

  private def rawUnqualifiedCall(
    targetType: ClassType,
    method: Method,
    params: Array[Term],
    context: LocalContext
  ): Term = {
    if ((method.modifier & AST.M_STATIC) != 0) {
      new CallStatic(targetType, method, params)
    } else if (context.isClosure) {
      new Call(new OuterThis(targetType), method, params)
    } else {
      new Call(new This(targetType), method, params)
    }
  }

  private def typeUnqualifiedMethodCallWithNamedArgs(
    node: AST.UnqualifiedMethodCall,
    context: LocalContext,
    expected: Type
  ): Option[Term] = {
    val targetType = bodyContext.definition
    val candidates = new JTreeSet[Method](new MethodComparator)
    calls.collectMethodsMatching(targetType, node.name, candidates, _ => true)
    if (candidates.isEmpty) {
      calls.reportMethodNotFound(node, targetType, node.name, Array[Type]())
      return None
    }

    overloadSupport.selectNamedArgumentMethod(candidates, node.args) match {
      case CandidateSelection.NoMatch =>
        calls.reportMethodNotFound(node, targetType, node.name, Array[Type]())
        None
      case CandidateSelection.Ambiguous(first, second) =>
        calls.reportAmbiguousMethod(node, first, second, node.name)
        None
      case CandidateSelection.Selected(method) =>
        if (!ensureInstanceAvailable(node, method, context)) return None
        val classSubst: scala.collection.immutable.Map[String, Type] = scala.collection.immutable.Map.empty
        calls.processNamedArguments(node, node.args, method, context).flatMap { params =>
          calls.buildResolvedCall(node, method, params, node.typeArgs, classSubst, expected)(
            expectedArgs => calls.processParamsWithExpected(node, params, expectedArgs),
            finalParams => rawUnqualifiedCall(targetType, method, finalParams, context)
          )
        }
    }
  }
}
