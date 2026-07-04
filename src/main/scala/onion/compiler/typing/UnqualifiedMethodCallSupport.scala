package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*
import onion.compiler.typing.session.TypingBodyContext

import java.util.{TreeSet => JTreeSet}

private[compiler] final class UnqualifiedMethodCallSupport(
  bodyContext: TypingBodyContext,
  calls: MethodCallTyping,
  fallback: MethodCallFallbackSupport,
  staticCalls: StaticMethodCallSupport
) {
  private val overloadSupport = new CallOverloadSupport(calls.typing, calls)
  private val callableValueCallSupport = new CallableValueCallSupport(bodyContext, calls)
  private val staticImportMethodCallSupport = new StaticImportMethodCallSupport(bodyContext, calls)

  /**
   * A `re"..."` literal desugars to `re("...")`, resolving to `onion.Resources.re`.
   * Its pattern is a compile-time constant, so validate it here and report E0059
   * (as `case re"..."` / `from re"..."` already do) instead of letting an invalid
   * pattern throw a raw PatternSyntaxException at run time.
   */
  private def checkRegexLiteral(node: AST.UnqualifiedMethodCall, term: Term): Unit = term match {
    case cs: CallStatic
      if cs.method.name == "re" && cs.method.affiliation != null &&
         cs.method.affiliation.name == "onion.Resources" && cs.parameters.length == 1 =>
      cs.parameters(0) match {
        case sv: StringValue =>
          try java.util.regex.Pattern.compile(sv.value)
          catch {
            case e: java.util.regex.PatternSyntaxException =>
              bodyContext.report(SemanticError.REGEX_PATTERN_INVALID, node, e.getDescription + " (at index " + e.getIndex + ")")
          }
        case _ =>
      }
    case _ =>
  }

  def typeUnqualifiedMethodCall(
    node: AST.UnqualifiedMethodCall,
    context: LocalContext,
    expected: Type = null
  ): Option[Term] = {
    if (ArgumentHelpers.hasNamedArguments(node.args)) {
      return typeUnqualifiedMethodCallWithNamedArgs(node, context, expected)
    }

    // A closure argument with untyped parameters cannot be typed before the
    // target parameter type is known. Mirror the instance/static paths: resolve
    // the overload first, then type each such closure against its resolved
    // functional-interface parameter type (issue #232). The eager path below
    // would otherwise fail with E0052 on the closure.
    val untypedClosureIndices = node.args.zipWithIndex.collect {
      case (expr, i) if isClosureWithUntypedParams(expr) => i
    }.toSet
    if (untypedClosureIndices.nonEmpty) {
      return typeUnqualifiedCallWithClosures(node, context, expected, untypedClosureIndices)
    }

    val params0 = calls.typedTerms(node.args.toArray, context)
    if (params0 == null) return None
    val targetType = bodyContext.definition
    val methods0 = MethodResolution.findMethods(targetType, node.name, params0, bodyContext.table)

    // When no method applies, an argument that is a generic call (e.g.
    // `Result::ok(7)`) may have left its type parameters unbound (-> Object).
    // Re-type such arguments against the single candidate's parameter types so
    // the expected type pins them, then retry resolution (issue #232).
    val (params, methods) =
      if (methods0.length == 0) {
        val topLevel = bodyContext.topLevelClass.filter(t => !(t eq targetType))
        val retyped =
          calls.retypeArgumentsForExpected(targetType, node.name, node.args, params0, context, _ => true)
            .orElse(topLevel.flatMap(top =>
              calls.retypeArgumentsForExpected(top, node.name, node.args, params0, context, m => (m.modifier & AST.M_STATIC) != 0)))
        retyped match {
          case Some(newParams) =>
            val remethods = MethodResolution.findMethods(targetType, node.name, newParams, bodyContext.table)
            if (remethods.length > 0) (newParams, remethods) else (newParams, methods0)
          case None => (params0, methods0)
        }
      } else (params0, methods0)

    if (methods.length == 0) {
      staticImportMethodCallSupport.resolveStaticImportMethodCall(node, params, expected) match {
        case MethodFallbackLookup.Found(term) =>
          checkRegexLiteral(node, term)
          Some(term)
        case MethodFallbackLookup.Error =>
          None
        case MethodFallbackLookup.NotFound =>
          callableValueCallSupport.resolveCallableValue(node, params, context, expected) match {
            case Some(term) =>
              Some(term)
            case None =>
              resolveTopLevelStaticCall(node, params, expected) match {
                case Some(term) => Some(term)
                case None =>
                  calls.reportMethodNotFound(node, targetType, node.name, calls.types(params))
                  None
              }
          }
      }
    } else if (methods.length > 1) {
      calls.reportAmbiguousMethods(node, node.name, methods)
      None
    } else {
      val method = methods(0)
      if (!ensureInstanceAvailable(node, method, context)) return None
      val classSubst = selfClassSubstitution(method)
      calls.buildResolvedCall(node, method, params, node.typeArgs, classSubst, expected, context)(
        expectedArgs => calls.prepareCallParams(node, node.args, method, params, expectedArgs),
        finalParams => rawUnqualifiedCall(targetType, method, finalParams, context)
      )
    }
  }

  private def hasNamedMethod(targetType: ClassType, name: String, filter: Method => Boolean): Boolean = {
    val candidates = new JTreeSet[Method](new MethodComparator)
    calls.collectMethodsMatching(targetType, name, candidates, filter)
    !candidates.isEmpty
  }

  /**
   * Bidirectional-inference entry for an unqualified call whose arguments include
   * a closure with untyped parameters (issue #232). Preserves the eager path's
   * resolution ORDER: the current class first, then the synthetic top-level class.
   *
   * A method on the current class is called unqualified as either an instance
   * method (routed through the instance bidirectional core with a synthesized
   * `this`/`OuterThis` receiver, mirroring `rawUnqualifiedCall`) or a static one
   * (routed through the static bidirectional core with the class as the type).
   * A top-level function is a static method on the top-level class. When no
   * target class declares a method of this name, resolution falls through to the
   * eager path so its remaining fallbacks (static-import, callable-value) and
   * not-found diagnostics still apply.
   */
  private def typeUnqualifiedCallWithClosures(
    node: AST.UnqualifiedMethodCall,
    context: LocalContext,
    expected: Type,
    untypedClosureIndices: Set[Int]
  ): Option[Term] = {
    val targetType = bodyContext.definition
    val name = node.name

    if (hasNamedMethod(targetType, name, calls.isInstanceMethod)) {
      // Instance method on the current class: needs a receiver, which is
      // unavailable in a static context (same rule the eager path enforces).
      val instanceCandidatesUsable = !context.isStatic
      if (instanceCandidatesUsable) {
        val receiver: Term =
          if (context.isClosure) new OuterThis(targetType) else new This(targetType)
        return fallback.typeCallWithBidirectionalInferenceCore(
          node, name, node.args, node.typeArgs, receiver, targetType, context, expected, untypedClosureIndices
        )
      }
      // In a static context, an instance-named method is not callable; fall
      // through to the static / top-level resolution below.
    }

    if (hasNamedMethod(targetType, name, calls.isStaticMethod)) {
      return staticCalls.typeStaticCallWithBidirectionalInferenceCore(
        node, name, node.args, node.typeArgs, targetType, context, expected, untypedClosureIndices
      )
    }

    bodyContext.topLevelClass match {
      case Some(top) if !(top eq targetType) && hasNamedMethod(top, name, calls.isStaticMethod) =>
        staticCalls.typeStaticCallWithBidirectionalInferenceCore(
          node, name, node.args, node.typeArgs, top, context, expected, untypedClosureIndices
        )
      case _ =>
        // No target class holds a method of this name. The remaining fallbacks
        // (static-import, callable-value) and the not-found diagnostic cannot
        // benefit from bidirectional inference on the closure, so report the
        // closure's parameter type as usual by delegating to the eager path,
        // which yields the standard not-found error for the arguments it can
        // type (the untyped closure still surfaces E0052 there only if a
        // callable value or static import is genuinely absent).
        val params0 = calls.typedTerms(node.args.toArray, context)
        if (params0 == null) return None
        staticImportMethodCallSupport.resolveStaticImportMethodCall(node, params0, expected) match {
          case MethodFallbackLookup.Found(term) =>
            checkRegexLiteral(node, term)
            Some(term)
          case MethodFallbackLookup.Error =>
            None
          case MethodFallbackLookup.NotFound =>
            callableValueCallSupport.resolveCallableValue(node, params0, context, expected) match {
              case some @ Some(_) => some
              case None =>
                calls.reportMethodNotFound(node, targetType, name, calls.types(params0))
                None
            }
        }
    }
  }

  private def hasUntypedParams(closure: AST.ClosureExpression): Boolean =
    closure.args.exists(_.typeRef == null) || ClosureBodyAnalysis.neverReturnsNormally(closure)

  private def isClosureWithUntypedParams(expr: AST.Expression): Boolean =
    expr match {
      case closure: AST.ClosureExpression => hasUntypedParams(closure)
      case _ => false
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

  /**
   * Substitution for an inherited method called unqualified (self-receiver).
   * The current class may extend a generic parent with concrete type arguments
   * (e.g. `class StrBox : Box[String]`); an inherited `Box.get(): T` must then be
   * seen as returning `String`. Build the substitution by composing the extends
   * clause bindings from the current class up to the method's declaring class,
   * exactly as the variable-receiver path does. When the method is declared on the
   * current class (or the chain carries no type arguments) this yields an empty
   * map, preserving prior behavior. (issue #271)
   */
  private def selfClassSubstitution(method: Method): scala.collection.immutable.Map[String, Type] =
    TypeSubstitution.hierarchySubstitution(bodyContext.definition, method.affiliation)

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

  /**
   * Fallback: a bare call may name a top-level function, which lives as a static
   * method on the synthetic top-level class. Reachable from any scope (other
   * classes' methods, closures, main) once top-level functions are static.
   */
  private def resolveTopLevelStaticCall(
    node: AST.UnqualifiedMethodCall,
    params: Array[Term],
    expected: Type
  ): Option[Term] =
    bodyContext.topLevelClass match {
      case Some(top) if !(top eq bodyContext.definition) =>
        val methods = MethodResolution.findMethods(top, node.name, params, bodyContext.table)
          .filter(m => (m.modifier & AST.M_STATIC) != 0)
        if (methods.length == 1) {
          val method = methods(0)
          val classSubst: scala.collection.immutable.Map[String, Type] = scala.collection.immutable.Map.empty
          calls.buildResolvedCall(node, method, params, node.typeArgs, classSubst, expected)(
            expectedArgs => calls.prepareCallParams(node, node.args, method, params, expectedArgs),
            finalParams => new CallStatic(top, method, finalParams)
          )
        } else None
      case _ => None
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
        val classSubst = selfClassSubstitution(method)
        calls.processNamedArguments(node, node.args, method, context).flatMap { params =>
          calls.buildResolvedCall(node, method, params, node.typeArgs, classSubst, expected)(
            expectedArgs => calls.processParamsWithExpected(node, params, expectedArgs),
            finalParams => rawUnqualifiedCall(targetType, method, finalParams, context)
          )
        }
    }
  }
}
