package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*
import onion.compiler.toolbox.Boxing
import scala.util.boundary
import scala.util.boundary.break

private[compiler] final class ExtensionMethodFallbackSupport(
  typing: Typing,
  calls: MethodCallTyping
) {
  private val overloadSupport = new CallOverloadSupport(typing, calls)
  private val mentionedNames = new MentionedNames

  /** The overload each lambda call site chose, per receiver, argument and expected types and
   *  what the call sees around it (a captured variable's type can differ between two
   *  overloads an enclosing lambda is tried against). */
  private val decisionsAt =
    new java.util.IdentityHashMap[AST.MethodCall, java.util.HashMap[(Type, List[Type], Type, AnyRef), ExtensionMethodDefinition]]()

  /** The keys under which a lambda call site fitted no overload, or two equally. */
  private val failedAt =
    new java.util.IdentityHashMap[AST.MethodCall, java.util.HashSet[(Type, List[Type], Type, AnyRef)]]()

  /** Two extensions of this name apply equally well. `tryExtensionMethodCall` has then
   *  reported E0006, so the caller must not add an E0005 saying none exists (#2446). */
  /** The call typed against a user-declared extension from its already-typed arguments.
   *  A call whose lambdas declare every parameter type needs no bidirectional re-typing, and
   *  re-typing the lambda bodies once per nesting level made nested calls exponential (#2463).
   *  None when there is no user extension of the name, or none fits unambiguously; otherwise
   *  the build's own result, errors reported as on the no-lambda path. */
  def tryUserExtensionWithTypedArguments(
    node: AST.MethodCall,
    target: Term,
    targetType: ObjectType,
    params: Array[Term],
    expected: Type
  ): Option[Option[Term]] =
    val user = collectExtensionMethods(targetType, node.name).filterNot(isBuiltinExtension)
    // One receiver type for all of them: overloads whose parameters and receivers could rank
    // in opposite directions (E0006) are left to the ranking in the bidirectional path.
    if (user.isEmpty || user.exists(m => !(m.receiverType eq user.head.receiverType))) None
    else selectApplicableExtensionMethod(targetType, node.name, params) match {
      case CandidateSelection.Selected(m) if !isBuiltinExtension(m) =>
        Some(buildExtensionCall(node, target, targetType, params, expected, m))
      case _ => None
    }

  def isAmbiguousExtensionCall(targetType: ObjectType, name: String, params: Array[Term]): Boolean =
    selectApplicableExtensionMethod(targetType, name, params).isInstanceOf[CandidateSelection.Ambiguous[?]]

  def tryExtensionMethodCall(
    node: AST.MethodCall,
    target: Term,
    targetType: ObjectType,
    params: Array[Term],
    expected: Type,
    reportIfNotFound: Boolean = true
  ): Option[Term] = {
    selectApplicableExtensionMethod(targetType, node.name, params) match {
      case CandidateSelection.NoMatch if arrayAsList(node, target, targetType).isDefined =>
        val (listTarget, listType) = arrayAsList(node, target, targetType).get
        tryExtensionMethodCall(node, listTarget, listType, params, expected, reportIfNotFound)
      case CandidateSelection.NoMatch =>
        // reportIfNotFound is false when the caller has a further fallback to try
        // (e.g. a bean-property getter) and will report the error itself if that fails.
        if (reportIfNotFound) calls.reportMethodNotFound(node, targetType, node.name, calls.types(params))
        None
      case CandidateSelection.Ambiguous(first, second) =>
        calls.reportAmbiguousSignature(
          node,
          first.containerClass,
          node.name,
          first.arguments,
          second.containerClass,
          node.name,
          second.arguments
        )
        None
      case CandidateSelection.Selected(extMethod) =>
        buildExtensionCall(node, target, targetType, params, expected, extMethod)
    }
  }

  /**
   * Resolve an operator convention method (plus/minus/times/div/rem) defined via
   * an `extension` block on the receiver type and build the backing static call.
   * Used by the operator-overloading path (`a + b`) so extensions are consulted
   * the same way an explicit `a.plus(b)` is. Reports nothing and returns None on
   * any miss, so the caller falls back to string concatenation / the numeric path.
   * The single argument is already a fully-typed term (no closures involved).
   */
  def tryExtensionOperatorMethod(
    node: AST.Node,
    name: String,
    target: Term,
    targetType: ObjectType,
    param: Term,
    expected: Type
  ): Option[Term] = {
    val params = Array(param)
    selectApplicableExtensionMethod(targetType, name, params) match {
      case CandidateSelection.Selected(extMethod) =>
        val containerClass = extMethod.containerClass
        val staticArgs = Array(staticReceiver(target, extMethod)) ++ params
        containerClass.findMethod(name, staticArgs) match {
          case Array(staticMethod) =>
            val classSubst = TypeSubstitution.classSubstitution(containerClass)
            // Infer a generic operator's type arguments (`plus[T](List[T], List[T])` on two
            // List[Int]s binds T = Int) instead of defaulting T to its Object bound, which made
            // List[Int] fail against List[Object]. Silent: a miss falls back like any other.
            val methodSubst = GenericMethodTypeArguments.infer(
              typing, node, staticMethod, staticArgs, classSubst, expected, reportErrors = false)
            val expectedArgs = TypeSubst.args(staticMethod, classSubst, methodSubst)
            // Silent, like the rest of this path: an argument that does not fit (`[1] + aStringList`)
            // must fall back to the caller's own operator error, not add an E0000 of its own.
            typing.withSuppressedReporting(calls.processParamsWithExpected(node, staticArgs, expectedArgs)).map { finalParams =>
              val call = new CallStatic(containerClass, staticMethod, finalParams)
              TypeSubst.withCast(call, TypeSubst.result(staticMethod.returnType, classSubst, methodSubst))
            }
          case _ => None
        }
      case _ => None
    }
  }

  private def selectApplicableExtensionMethod(
    targetType: ObjectType,
    name: String,
    params: Array[Term]
  ): CandidateSelection[ExtensionMethodDefinition] =
    targetType match {
      // Class receivers and array receivers both resolve through the extension
      // registry (collectExtensionMethods handles the <array> pseudo-receiver);
      // without the ArrayType case a no-closure call like `arr.toList()` would
      // never find its builtin array extension (the closure path already does).
      case _: ClassType | _: ArrayType =>
        val applicable = collectExtensionMethods(targetType, name).filter { extMethod =>
          extMethod.name == name && isExtensionMethodApplicable(extMethod, params)
        }
        // A user-declared `extension` shadows a builtin stdlib extension
        // (onion.Colls / onion.Iterables) of the same name+arity: an explicit
        // user extension expresses clear intent and should win rather than
        // collide as an ambiguity.
        val userDefined = applicable.filterNot(isBuiltinExtension)
        val pool = if (userDefined.nonEmpty) userDefined else applicable
        // As in Java overload resolution, a candidate whose receiver type is more specific
        // than every other one's wins: `aSet.toList()` takes `toList(Set)`, not
        // `toList(Iterable)`, instead of being reported as ambiguous.
        val chosen = pool.filter { m =>
          pool.forall(o => (o eq m) || isMoreSpecificReceiver(m.receiverType, o.receiverType))
        } match {
          case Seq(single) => Seq(single)
          case _ => pool
        }
        // Then the rule class methods use for the remaining overloads, with the receiver as
        // the first parameter: the one that is at least as specific everywhere wins
        // (`ex(Char)` over `ex(Long)` for 'A').
        val candidates = chosen.map(m => ApplicableMethod(m, m.receiverType +: m.arguments, Map.empty)).toList
        val argTypes = (targetType: Type) +: calls.types(params)
        overloadSupport.selectMostSpecificApplicable(candidates, argTypes = argTypes) match {
          case CandidateSelection.NoMatch => CandidateSelection.NoMatch
          case CandidateSelection.Ambiguous(first, second) =>
            CandidateSelection.Ambiguous(
              first.method.asInstanceOf[ExtensionMethodDefinition],
              second.method.asInstanceOf[ExtensionMethodDefinition])
          case CandidateSelection.Selected(am) =>
            val winner = am.method.asInstanceOf[ExtensionMethodDefinition]
            // The winner's receiver must also be at least as specific as every other's.
            // Unrelated receivers (`extension I`, `extension J` on a `C conforms I, J`) or
            // `List` beside `List[Int]` stay ambiguous, whatever order they are declared in.
            chosen.find { o =>
              !(o eq winner) && !(o.receiverType eq winner.receiverType) &&
                !isMoreSpecificReceiver(winner.receiverType, o.receiverType)
            } match {
              case Some(other) => CandidateSelection.Ambiguous(winner, other)
              case None => CandidateSelection.Selected(winner)
            }
        }
      case _ =>
        CandidateSelection.NoMatch
    }

  /** A `List` pipeline call on an array of references (`line.split(" ").distinct()`):
   *  the array as `toList()` and that `List` type, when the array has no extension of
   *  the called name itself but `List` does. `map`/`filter` keep their own array
   *  overloads; an array of primitives (`Int[]`) is left alone, as `toList` takes `T[]`. */
  private def arrayAsList(node: AST.MethodCall, target: Term, targetType: ObjectType): Option[(Term, ObjectType)] =
    targetType match {
      case at: ArrayType if (at.dimension > 1 || !at.component.isBasicType) &&
          collectExtensionMethods(targetType, node.name).isEmpty =>
        selectApplicableExtensionMethod(targetType, "toList", Array.empty) match {
          case CandidateSelection.Selected(toList) =>
            val toListNode = node.copy(name = "toList", args = Nil, typeArgs = Nil)
            buildExtensionCall(toListNode, target, targetType, Array.empty, null, toList).collect {
              case term if term.`type`.isInstanceOf[ObjectType] &&
                  collectExtensionMethods(term.`type`.asInstanceOf[ObjectType], node.name).nonEmpty =>
                (term, term.`type`.asInstanceOf[ObjectType])
            }
          case _ => None
        }
      case _ => None
    }

  /** `a` is a strict subtype of `b` (raw class comparison, so `Set[T]` is below `Iterable[T]`). */
  private def isMoreSpecificReceiver(a: Type, b: Type): Boolean =
    (a, b) match {
      case (ac: ClassType, bc: ClassType) =>
        val (ar, br) = (rawOf(ac), rawOf(bc))
        ar.name != br.name && TypeRules.isSuperType(br, ar)
      case _ => false
    }

  private def rawOf(ct: ClassType): ClassType = ct match {
    case ap: AppliedClassType => ap.raw
    case other => other
  }

  private def staticReceiver(target: Term, extMethod: ExtensionMethodDefinition): Term =
    extMethod.receiverType match {
      case bt: BasicType => Boxing.unboxing(typing.table_, target, bt)
      case _ => target
    }

  /**
   * For safe-call extension lookup: the target may be NullableType-wrapped
   * (String?) rather than the inner type (String). Wrap it in AsInstanceOf to
   * give it the non-nullable inner type so findMethod can match the static
   * method's receiver parameter.
   */
  private def nonNullableReceiverForLookup(target: Term, extMethod: ExtensionMethodDefinition): Term =
    target.`type` match {
      case nt: NullableType =>
        // Reference nullable (e.g. String?) — strip the wrapper
        nt.innerType match {
          case objType: ObjectType => new AsInstanceOf(target, objType)
          case _ => target
        }
      case _ =>
        // Already non-nullable (primitives arrive as the boxed class type after
        // normalizeSafeMethodCallTarget wraps them in AsInstanceOf)
        target
    }

  /**
   * Build a SafeStaticExtensionCall for a ?. call that resolved to an extension method.
   * `target` is the nullable (boxed) receiver; it is stored in the node for the
   * null-check that the codegen emits. `params` are the non-receiver user arguments.
   */
  private def buildSafeExtensionCall(
    node: AST.SafeMethodCall,
    target: Term,
    targetType: ObjectType,
    params: Array[Term],
    expected: Type,
    extMethod: ExtensionMethodDefinition
  ): Option[Term] = {
    val containerClass = extMethod.containerClass
    val receiverBasicType: Option[BasicType] = extMethod.receiverType match {
      case bt: BasicType => Some(bt)
      case _ => None
    }
    // Build the lookup receiver with non-nullable inner type so findMethod matches
    val lookupReceiver = staticReceiver(nonNullableReceiverForLookup(target, extMethod), extMethod)
    val staticArgs = Array(lookupReceiver) ++ params

    containerClass.findMethod(node.name, staticArgs) match {
      case Array() =>
        calls.reportMethodNotFound(node, targetType, node.name, calls.types(params))
        None
      case Array(staticMethod) =>
        val classSubst = TypeSubstitution.classSubstitution(containerClass)
        calls.buildResolvedCall(node, staticMethod, staticArgs, node.typeArgs, classSubst, expected)(
          expectedArgs => calls.processParamsWithExpected(node, staticArgs, expectedArgs),
          // finalParams(0) is the processed receiver; drop it — codegen handles
          // the receiver from node.target (null-check + optional unbox)
          finalParams => new SafeStaticExtensionCall(node.location, target, containerClass, staticMethod, receiverBasicType, finalParams.tail)
        )
      case multiple =>
        calls.reportAmbiguousMethods(node, node.name, multiple)
        None
    }
  }

  /**
   * Try to resolve `node` (a safe ?. call) against the extension registry.
   * Returns None and reports an error when not found.
   */
  def tryExtensionMethodCallForSafeNav(
    node: AST.SafeMethodCall,
    target: Term,
    targetType: ObjectType,
    params: Array[Term],
    expected: Type
  ): Option[Term] =
    selectApplicableExtensionMethod(targetType, node.name, params) match {
      case CandidateSelection.NoMatch =>
        calls.reportMethodNotFound(node, targetType, node.name, calls.types(params))
        None
      case CandidateSelection.Ambiguous(first, second) =>
        calls.reportAmbiguousSignature(
          node,
          first.containerClass, node.name, first.arguments,
          second.containerClass, node.name, second.arguments
        )
        None
      case CandidateSelection.Selected(extMethod) =>
        buildSafeExtensionCall(node, target, targetType, params, expected, extMethod)
    }

  /**
   * Safe-nav variant of `tryZeroArgExtensionAccess`: `expr?.name` resolves a
   * zero-arg extension method (`def name: T` in an `extension` block) accessed
   * in property style (no parentheses), wrapping the result as a
   * SafeStaticExtensionCall so a null receiver short-circuits to null at
   * runtime instead of being dereferenced. Called from the SafeMemberSelection
   * typing path before it reports E0004, mirroring how tryZeroArgExtensionAccess
   * shadows E0004 on the plain (non-null-safe) member-selection path.
   */
  def tryZeroArgExtensionAccessForSafeNav(
    node: AST.Node,
    name: String,
    target: Term,
    targetType: ObjectType,
    expected: Type
  ): Option[Term] = {
    val params = Array.empty[Term]
    selectApplicableExtensionMethod(targetType, name, params) match {
      case CandidateSelection.NoMatch => None
      case CandidateSelection.Ambiguous(first, second) =>
        calls.reportAmbiguousSignature(
          node,
          first.containerClass, name, first.arguments,
          second.containerClass, name, second.arguments
        )
        None
      case CandidateSelection.Selected(extMethod) =>
        val containerClass = extMethod.containerClass
        val receiverBasicType: Option[BasicType] = extMethod.receiverType match {
          case bt: BasicType => Some(bt)
          case _ => None
        }
        val lookupReceiver = staticReceiver(nonNullableReceiverForLookup(target, extMethod), extMethod)
        val staticArgs = Array(lookupReceiver)
        containerClass.findMethod(name, staticArgs) match {
          case Array(staticMethod) =>
            val classSubst = TypeSubstitution.classSubstitution(containerClass)
            calls.buildResolvedCall(node, staticMethod, staticArgs, Nil, classSubst, expected)(
              expectedArgs => calls.processParamsWithExpected(node, staticArgs, expectedArgs),
              finalParams => new SafeStaticExtensionCall(node.location, target, containerClass, staticMethod, receiverBasicType, finalParams.tail)
            )
          case _ => None
        }
    }
  }

  private def buildExtensionCall(
    node: AST.MethodCall,
    target: Term,
    targetType: ObjectType,
    params: Array[Term],
    expected: Type,
    extMethod: ExtensionMethodDefinition
  ): Option[Term] = {
    val containerClass = extMethod.containerClass
    val staticArgs = Array(staticReceiver(target, extMethod)) ++ params
    val staticMethods = backingStaticMethod(extMethod) match {
      case Some(method) => Array(method)
      case None => containerClass.findMethod(node.name, staticArgs)
    }

    staticMethods match {
      case Array() =>
        calls.reportMethodNotFound(node, targetType, node.name, calls.types(params))
        None
      case Array(staticMethod) =>
        val classSubst = TypeSubstitution.classSubstitution(containerClass)
        calls.buildResolvedCall(node, staticMethod, staticArgs, node.typeArgs, classSubst, expected)(
          expectedArgs => calls.processParamsWithExpected(node, staticArgs, expectedArgs),
          finalParams => new CallStatic(containerClass, staticMethod, finalParams)
        )
      case multiple =>
        calls.reportAmbiguousMethods(node, node.name, multiple)
        None
    }
  }

  /** The container's static method that implements `extMethod`: the receiver, then the
   *  same parameter types. Looked up by signature, not re-resolved against the arguments,
   *  so the call runs the overload that selection chose (`ez(Object)` for an `Int`). */
  private def backingStaticMethod(extMethod: ExtensionMethodDefinition): Option[Method] = {
    val formals = extMethod.arguments
    extMethod.containerClass.methods(extMethod.name).filter { m =>
      Modifier.isStatic(m.modifier) && m.arguments.length == formals.length + 1 &&
        m.arguments(0).name == extMethod.receiverType.name &&
        formals.indices.forall(i => m.arguments(i + 1).name == formals(i).name)
    } match {
      case Array(single) => Some(single)
      case _ => None
    }
  }

  /**
   * Try to resolve a zero-arg extension method accessed in property style (no
   * parentheses): `expr.name` where `name` is declared as `def name: T = ...`
   * in an `extension` block. Called from the MemberSelection typing path before
   * it reports E0004 (field not found), so an extension zero-arg method shadows
   * the error rather than being missed entirely.
   */
  def tryZeroArgExtensionAccess(
    node: AST.Node,
    name: String,
    target: Term,
    targetType: ObjectType,
    expected: Type
  ): Option[Term] = {
    val params = Array.empty[Term]
    selectApplicableExtensionMethod(targetType, name, params) match {
      case CandidateSelection.NoMatch => None
      case CandidateSelection.Ambiguous(first, second) =>
        calls.reportAmbiguousSignature(
          node,
          first.containerClass, name, first.arguments,
          second.containerClass, name, second.arguments
        )
        None
      case CandidateSelection.Selected(extMethod) =>
        val containerClass = extMethod.containerClass
        val staticArgs = Array(staticReceiver(target, extMethod))
        containerClass.findMethod(name, staticArgs) match {
          case Array(staticMethod) =>
            val classSubst = TypeSubstitution.classSubstitution(containerClass)
            calls.buildResolvedCall(node, staticMethod, staticArgs, Nil, classSubst, expected)(
              expectedArgs => calls.processParamsWithExpected(node, staticArgs, expectedArgs),
              finalParams => new CallStatic(containerClass, staticMethod, finalParams)
            )
          case _ => None
        }
    }
  }

  /**
   * Bidirectional variant for calls whose arguments contain closures with
   * untyped parameters (list.map { x => ... }): pick the extension's backing
   * static method first, then type each closure against its parameter type.
   * Assumes closures are trailing arguments (same as the instance-call path).
   */
  def tryExtensionMethodCallBidirectional(
    node: AST.MethodCall,
    target: Term,
    targetType: ObjectType,
    context: LocalContext,
    expected: Type,
    untypedClosureIndices: Set[Int]
  ): Option[Term] = boundary {
    val name = node.name
    val args = node.args.toArray

    val preliminaryParams = new Array[Term](args.length)
    for (i <- args.indices if !untypedClosureIndices.contains(i)) {
      calls.typed(args(i), context) match {
        case Some(term) => preliminaryParams(i) = term
        case None => break(None)
      }
    }

    val candidates = collectExtensionMethods(targetType, name).filter(_.arguments.length == args.length)
    if (candidates.isEmpty) arrayAsList(node, target, targetType) match {
      case Some((listTarget, listType)) =>
        break(tryExtensionMethodCallBidirectional(node, listTarget, listType, context, expected, untypedClosureIndices))
      case None =>
    }
    // The call typed against one overload, with the substituted parameter types and the
    // argument types, which the ranking below compares as the class path does.
    def attemptRanked(extMethod: ExtensionMethodDefinition): Option[(Term, Array[Type], Array[Type])] = {
      val container = extMethod.containerClass
      val receiverType = staticReceiver(target, extMethod).`type`
      val statics = container.methods(name).filter { m =>
        Modifier.isStatic(m.modifier) && m.arguments.length == args.length + 1 &&
          receiverKindMatches(m.arguments(0), receiverType)
      }
      // The static that implements this very overload, when the container has one with its
      // parameter types; otherwise two same-arity overloads both ran the first-sorted one (#2447).
      val ownStatic = statics.filter { m =>
        extMethod.arguments.indices.forall(i => m.arguments(i + 1).name == extMethod.arguments(i).name)
      }
      (if (ownStatic.nonEmpty) ownStatic else statics)
        // Pick the overload whose first (receiver) parameter is the most specific
        // that still accepts the receiver — e.g. map(Set) over map(Iterable) for a
        // Set — instead of the first declared, which could be a sibling like
        // map(List) that does not actually accept it.
        .sortWith((a, b) => TypeRules.isSuperType(b.arguments(0), a.arguments(0)))
        .headOption
        .flatMap { method =>
          val classSubst = TypeSubstitution.classSubstitution(container)
          // Keep positions aligned with the backing static's formals (receiver at
          // index 0, then each call argument): closure slots stay null and are
          // skipped by inferWithoutDefaults, so any resolved argument still
          // unifies against its own formal regardless of order (#256).
          val knownStatic = Array(staticReceiver(target, extMethod)) ++ preliminaryParams
          val preliminarySubst = GenericMethodTypeArguments.inferWithoutDefaults(
            typing, node, method, knownStatic, classSubst, expected
          )
          val preliminaryExpected = method.arguments.map { argType =>
            TypeSubstitution.substituteType(argType, classSubst, preliminarySubst, defaultToBound = false)
          }

          var failed = false
          val finalArgs = new Array[Term](args.length)
          for (i <- args.indices) {
            if (untypedClosureIndices.contains(i)) {
              val expectedType = if (i + 1 < preliminaryExpected.length) preliminaryExpected(i + 1) else null
              calls.typed(args(i), context, expectedType) match {
                case Some(term) => finalArgs(i) = term
                case None => failed = true
              }
            } else {
              finalArgs(i) = preliminaryParams(i)
            }
          }
          if (failed) None
          else {
            val staticArgs = Array(staticReceiver(target, extMethod)) ++ finalArgs
            val finalSubst = GenericMethodTypeArguments.infer(typing, node, method, staticArgs, classSubst, expected)
            val finalExpectedArgs = TypeSubst.args(method, classSubst, finalSubst)
            calls.processParamsWithExpected(node, staticArgs, finalExpectedArgs).map { finalParams =>
              val call = new CallStatic(container, method, finalParams)
              (TypeSubst.withCast(call, TypeSubst.result(method.returnType, classSubst, finalSubst)),
                finalExpectedArgs.drop(1), finalArgs.map(_.`type`))
            }
          }
        }
    }
    def attempt(extMethod: ExtensionMethodDefinition): Option[Term] = attemptRanked(extMethod).map(_._1)

    // Several user overloads: the rule written on #2447. Type the call against each one
    // without reporting and keep the fits. A fit that takes each lambda as it is (a function
    // type, `Object`, a type variable) beats one that needs a SAM conversion (`Runnable`).
    // Then a fit wins over another when its parameters rank higher, as for class methods,
    // and its receiver is not less specific; or when the parameters tie and its receiver is
    // more specific. Parameters and receiver disagreeing, or no winner, is one E0006.
    // An overload with no lambda slot where a lambda is passed (`Int`, `List`) cannot fit and
    // is never typed against, so nested lambdas are typed once per real candidate. When
    // only SAM conversions fit, the void/value shape of the body decides, as for classes.
    val user = candidates.filterNot(isBuiltinExtension)
    if (user.length >= 2) {
      // The winner is remembered per call site, receiver, argument, expected types and what
      // the call sees: a nested call is typed again for each overload its enclosing lambda is
      // tried against, and repeating the whole choice at every level made nesting
      // exponential. A remembered winner that no longer fits falls back to the full choice
      // while errors are suppressed (an enclosing overload is being tried); when they are
      // reported, its errors are the call's own.
      val decisionKey = (targetType: Type, preliminaryParams.toList.map(p => if (p == null) null else p.`type`), expected, context.typingViewFor(mentionedNames.of(node.args)))
      val decisions = decisionsAt.computeIfAbsent(node, _ => new java.util.HashMap())
      val remembered = decisions.get(decisionKey)
      if (remembered != null) {
        if (typing.reportingEnabled) break(attempt(remembered))
        attempt(remembered) match {
          case some @ Some(_) => break(some)
          case None =>
        }
      }
      // A call that fitted no overload, or two equally, under this key does so again; while
      // an enclosing overload is only being tried, that answer is reused instead of the
      // whole choice (#2452). When errors are reported, the choice runs to report them.
      val failures = failedAt.computeIfAbsent(node, _ => new java.util.HashSet())
      if (!typing.reportingEnabled && failures.contains(decisionKey)) break(None)
      def decided(winner: ExtensionMethodDefinition): Option[Term] = {
        decisions.put(decisionKey, winner)
        attempt(winner)
      }
      val lambdaCapable = user.filter { m =>
        untypedClosureIndices.forall(i => i >= m.arguments.length || isLambdaSlot(m.arguments(i)))
      }
      if (lambdaCapable.length == 1) break(decided(lambdaCapable.head))
      // A primitive argument the parameter only takes narrowed (an `Int` literal for `Short`).
      def narrows(params: Array[Type]): Boolean = preliminaryParams.indices.exists { i =>
        val arg = preliminaryParams(i)
        arg != null && i < params.length && arg.`type`.isBasicType && params(i).isBasicType &&
          !TypeRules.isAssignable(params(i), arg.`type`)
      }
      val fits0 =
        if (lambdaCapable.isEmpty) Seq.empty
        else {
          // An overload that a fitting one always beats is not tried: the fitting one is
          // non-generic, takes every lambda directly (so rule 3 cannot prefer the other) and
          // has strictly more specific parameters and a receiver no less specific (rule 4).
          // Trying the most specific first lets `w(f)` beside `w(o: Object)` skip the
          // `Object` try, so nesting such calls does not double the work at every level.
          def dominates(a: ExtensionMethodDefinition, b: ExtensionMethodDefinition): Boolean =
            a.typeParameters.isEmpty && b.typeParameters.isEmpty && !narrows(a.arguments) &&
              untypedClosureIndices.forall(i => i >= a.arguments.length || takesLambdaDirectly(a.arguments(i))) &&
              TypeRules.isAllSuperType(b.arguments, a.arguments) && !TypeRules.isAllSuperType(a.arguments, b.arguments) &&
              !isMoreSpecificReceiver(b.receiverType, a.receiverType)
          val ordered = lambdaCapable.sortBy(m => -lambdaCapable.count(o => (o ne m) && dominates(m, o)))
          val found = scala.collection.mutable.ArrayBuffer[(ExtensionMethodDefinition, Array[Type], Array[Type])]()
          ordered.foreach { m =>
            if (!found.exists(f => dominates(f._1, m))) {
              // A try: its errors and warnings are not the call's; the winner is typed again below.
              typing.warningReporter_.discarding(typing.withSuppressedReporting(attemptRanked(m)))
                .foreach(r => found += ((m, r._2, r._3)))
            }
          }
          // Keep declaration order for the ranking and its E0006 message.
          lambdaCapable.flatMap(m => found.find(_._1 eq m))
        }
      if (fits0.isEmpty) {
        val builtins = candidates.filter(isBuiltinExtension)
        if (builtins.isEmpty) failures.add(decisionKey)
        break(if (builtins.isEmpty) attempt(closestByArity(user, args)) else builtins.iterator.flatMap(attempt).nextOption())
      }
      // A fit that takes each argument as it is (identity, widening, boxing) beats one that
      // needs a primitive narrowed: `5` goes to `h(Int, f)`, not `h(Short, f)`, as for class
      // methods, which never pick a narrowing overload while another one applies.
      val fitsAsIs = fits0.filterNot { case (_, expectedArgs, _) => narrows(expectedArgs) }
      val fits = if (fitsAsIs.nonEmpty) fitsAsIs else fits0
      val direct = fits.filter { case (m, _, _) =>
        untypedClosureIndices.forall(i => i >= m.arguments.length || takesLambdaDirectly(m.arguments(i)))
      }
      val pool =
        if (direct.nonEmpty) direct
        else {
          val shaped = overloadSupport.disambiguateClosureOverloads(
            fits.map { case (m, expectedArgs, _) => ApplicableMethod(m, expectedArgs, Map.empty) }.toList,
            untypedClosureIndices,
            calls.closureSamMatcher(node.args, context)
          ).map(_.method)
          fits.filter { case (m, _, _) => shaped.exists(_ eq m) }
        }
      // An untyped lambda's own position does not rank (rule 4 on #2447), as on the class
      // path: `(Long) -> Long` and `(Int) -> Int` never compare, so the other arguments
      // decide. With nothing else to compare, every position counts.
      val ranked = args.indices.filterNot(i => ArgumentHelpers.isClosureWithUntypedParams(args(i)))
      def beats(a: (ExtensionMethodDefinition, Array[Type], Array[Type]), b: (ExtensionMethodDefinition, Array[Type], Array[Type])): Boolean = {
        val params = rankParameters(a._1, a._2, b._1, b._2, a._3, ranked)
        val receiver =
          if (isMoreSpecificReceiver(a._1.receiverType, b._1.receiverType)) -1
          else if (isMoreSpecificReceiver(b._1.receiverType, a._1.receiverType)) 1
          else 0
        (params < 0 && receiver <= 0) || (params == 0 && receiver < 0)
      }
      pool.find(a => pool.forall(b => (a eq b) || beats(a, b))) match {
        case Some((winner, _, _)) => break(decided(winner))
        case None =>
          failures.add(decisionKey)
          val unbeaten = pool.filter(a => !pool.exists(b => (a ne b) && beats(b, a)))
          val (first, second) = unbeaten match {
            case Seq(x, y, _*) => (x._1, y._1)
            case _ => (pool(0)._1, pool(1)._1)
          }
          calls.reportAmbiguousSignature(node, first.containerClass, name, first.arguments, second.containerClass, name,
            second.arguments, SemanticErrorReporter.LambdaOverloadHint)
          break(None)
      }
    }
    candidates.iterator.flatMap(attempt).nextOption()
  }

  /** -1 when parameters `a` rank above `b` the way class overloads do (subtypes, then the
   *  primitive tiebreak for `argTypes`: `Int` over `Object`, `Integer` or a type variable),
   *  1 for the reverse, 0 when they are equal or cannot be ranked. */
  private def rankParameters(
    methodA: Method, a: Array[Type], methodB: Method, b: Array[Type], argTypes: Array[Type],
    ranked: Seq[Int] = Nil
  ): Int = {
    val positions = if (ranked.isEmpty) a.indices else ranked
    val (pa, pb) = (positions.map(a(_)).toArray, positions.map(b(_)).toArray)
    if (TypeRules.isAllSuperType(pa, pb) && TypeRules.isAllSuperType(pb, pa)) {
      // Equal once type arguments are substituted: the non-generic overload wins, as for
      // class methods (`each2(String, g)` over `each2[A](A, g)`).
      (methodA.typeParameters.isEmpty, methodB.typeParameters.isEmpty) match {
        case (true, false) => -1
        case (false, true) => 1
        case _ => 0
      }
    } else {
      val (ma, mb) = (ApplicableMethod(methodA, a, Map.empty), ApplicableMethod(methodB, b, Map.empty))
      overloadSupport.selectMostSpecificApplicable(List(ma, mb), relevantIndices = ranked, argTypes = argTypes) match {
        case CandidateSelection.Selected(am) => if (am eq ma) -1 else 1
        case _ => 0
      }
    }
  }

  /** A parameter a lambda can be passed to (rule 1 on #2447): an Onion function type,
   *  `Object`, a type variable, or an interface with one abstract method (not counting
   *  `Object`'s). Never a primitive, an array, a class, or an interface like `List`. */
  private def isLambdaSlot(param: Type): Boolean = param match {
    case _: TypeVariableType => true
    case ct: ClassType =>
      if (ct.name.startsWith("onion.Function") || ct.name == "java.lang.Object") true
      else if (!ct.isInterface) false
      else {
        val raw = rawOf(ct)
        raw.methods.filter(m => Modifier.isAbstract(m.modifier) && !isPublicObjectMethod(m)).map(_.name).distinct.length <= 1
      }
    case _ => false
  }

  private def isPublicObjectMethod(m: Method): Boolean = m.name match {
    case "equals" => m.arguments.length == 1 && m.arguments(0).name == "java.lang.Object"
    case "hashCode" | "toString" => m.arguments.isEmpty
    case _ => false
  }

  /** A parameter a lambda reaches with no SAM conversion: an Onion function type, `Object`
   *  or a type variable. */
  private def takesLambdaDirectly(param: Type): Boolean = param match {
    case _: TypeVariableType => true
    case ct: ClassType => ct.name.startsWith("onion.Function") || ct.name == "java.lang.Object"
    case _ => false
  }

  /** When no overload fits, the one to report against (rule 2 on #2447): the first whose
   *  function-typed parameters take as many arguments as the lambdas passed to them; else
   *  the first with a lambda slot at every lambda's position; else the first. */
  private def closestByArity(overloads: Seq[ExtensionMethodDefinition], args: Array[AST.Expression]): ExtensionMethodDefinition = {
    def atLambdas(slot: (AST.ClosureExpression, Type) => Boolean)(m: ExtensionMethodDefinition): Boolean =
      args.indices.forall { i =>
        args(i) match {
          case c: AST.ClosureExpression if i < m.arguments.length => slot(c, m.arguments(i))
          case _ => true
        }
      }
    val sameArity = atLambdas {
      case (c, ct: ClassType) if ct.name.startsWith("onion.Function") => ct.name == "onion.Function" + c.args.length
      case _ => false
    }
    val functional = atLambdas((_, param) => isLambdaSlot(param))
    overloads.find(sameArity).orElse(overloads.find(functional)).getOrElse(overloads.head)
  }

  /** The backing static's first parameter must accept this receiver: array kinds
   *  must agree (an array-taking overload must not be picked for a List receiver
   *  and vice versa), and for reference receivers the receiver must be assignable
   *  to the parameter type — so a `map(List)` overload is not chosen for a `Set`. */
  private def receiverKindMatches(firstParam: Type, receiver: Type): Boolean =
    (firstParam, receiver) match {
      case (_: ArrayType, _: ArrayType) => true
      case (_: ArrayType, _) => false
      case (_, _: ArrayType) => false
      case _ => calls.isAssignableWithBoxing(firstParam, receiver)
    }

  private def collectExtensionMethods(
    targetType: ObjectType,
    name: String
  ): Seq[ExtensionMethodDefinition] =
    // The receiver hierarchy walk and the per-type name filter depend only on the
    // (receiver type, name) pair; user extensions are all registered before body typing.
    typing.table_.extensionCandidatesOf(targetType, name)(collectExtensionMethodsUncached(targetType, name))

  private def collectExtensionMethodsUncached(
    targetType: ObjectType,
    name: String
  ): Seq[ExtensionMethodDefinition] = {
    val result = scala.collection.mutable.LinkedHashSet.empty[ExtensionMethodDefinition]

    def collect(currentType: ObjectType): Unit = {
      if (currentType == null) return
      currentType match {
        case classType: ClassType =>
          typing.lookupExtensionMethods(classType.name).foreach { extMethod =>
            if (extMethod.name == name) result += extMethod
          }
        case at: ArrayType =>
          typing.lookupExtensionMethods(Typing.ArrayExtensionReceiver).foreach { extMethod =>
            if (extMethod.name == name && arrayReceiverAccepts(extMethod.receiverType, at)) result += extMethod
          }
        case _ =>
      }
      collect(currentType.superClass)
      currentType.interfaces.foreach(collect)
    }

    collect(targetType)
    result.toSeq
  }

  /** A `T[]` receiver cannot bind `T` to a primitive, as in Java (#2458), so a primitive
   *  array (`Int[]`) is matched only against a receiver of exactly its own array type. */
  private def arrayReceiverAccepts(receiverType: Type, actual: ArrayType): Boolean = {
    def primitiveArray(t: Type): Boolean = t match {
      case a: ArrayType => a.dimension == 1 && a.component.isBasicType
      case _ => false
    }
    if (primitiveArray(actual) || primitiveArray(receiverType)) receiverType match {
      case a: ArrayType => a.dimension == actual.dimension && a.component == actual.component
      case _ => false
    }
    else receiverType match {
      case a: ArrayType => TypeRules.isAssignable(a, actual)
      case _ => true
    }
  }

  /** A builtin stdlib extension (the bundled collection/string utilities), as
   *  opposed to a user-declared `extension` block — used so a user extension can
   *  shadow a builtin of the same name instead of colliding as an ambiguity. */
  private def isBuiltinExtension(m: ExtensionMethodDefinition): Boolean =
    ExtensionMethodFallbackSupport.BuiltinExtensionContainers.contains(m.containerClass.name)

  private def isExtensionMethodApplicable(
    extMethod: ExtensionMethodDefinition,
    params: Array[Term]
  ): Boolean = {
    val expectedArgs = extMethod.arguments
    params.length == expectedArgs.length &&
    params.indices.forall { i =>
      calls.isAssignableWithBoxing(expectedArgs(i), params(i).`type`)
    }
  }
}

private[compiler] object ExtensionMethodFallbackSupport {
  /** The single source of truth for which stdlib containers' static helpers are
   *  registered as builtin extension methods -- consumed both by
   *  `Typing.registerBuiltinExtensions` (which registers them, in this order: for
   *  an overlapping receiver+name+signature the first container listed wins) and by
   *  `isBuiltinExtension` above (so a user-declared `extension` of the same name
   *  shadows these rather than colliding as an ambiguity). Kept as an ordered `Seq`
   *  rather than a `Set` so that registration order -- and with it, which container
   *  wins an overlap -- stays defined. */
  val BuiltinExtensionContainers: Seq[String] =
    Seq("onion.Colls", "onion.Iterables", "onion.Maps", "onion.Strings", "onion.Sets", "onion.Hash", "onion.Codec", "onion.Text", "onion.Stats", "onion.Format")
}
