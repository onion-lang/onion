package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*
import onion.compiler.toolbox.Boxing

private[compiler] final class ExtensionMethodFallbackSupport(
  typing: Typing,
  calls: MethodCallTyping
) {
  def tryExtensionMethodCall(
    node: AST.MethodCall,
    target: Term,
    targetType: ObjectType,
    params: Array[Term],
    expected: Type,
    reportIfNotFound: Boolean = true
  ): Option[Term] = {
    selectApplicableExtensionMethod(targetType, node.name, params) match {
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
            val methodSubst = scala.collection.immutable.Map.empty[String, Type]
            val expectedArgs = TypeSubst.args(staticMethod, classSubst, methodSubst)
            calls.processParamsWithExpected(node, staticArgs, expectedArgs).map { finalParams =>
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
        val chosen = if (userDefined.nonEmpty) userDefined else applicable
        if (chosen.isEmpty) CandidateSelection.NoMatch
        else if (chosen.length > 1) CandidateSelection.Ambiguous(chosen(0), chosen(1))
        else CandidateSelection.Selected(chosen.head)
      case _ =>
        CandidateSelection.NoMatch
    }

  private def staticReceiver(target: Term, extMethod: ExtensionMethodDefinition): Term =
    extMethod.receiverType match {
      case bt: BasicType => Boxing.unboxing(typing.table_, target, bt)
      case _ => target
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
    val staticMethods = containerClass.findMethod(node.name, staticArgs)

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
  ): Option[Term] = {
    val name = node.name
    val args = node.args.toArray

    val preliminaryParams = new Array[Term](args.length)
    for (i <- args.indices if !untypedClosureIndices.contains(i)) {
      calls.typed(args(i), context) match {
        case Some(term) => preliminaryParams(i) = term
        case None => return None
      }
    }

    val candidates = collectExtensionMethods(targetType, name).filter(_.arguments.length == args.length)
    candidates.iterator.flatMap { extMethod =>
      val container = extMethod.containerClass
      val receiverType = staticReceiver(target, extMethod).`type`
      container.methods(name)
        .filter { m =>
          Modifier.isStatic(m.modifier) && m.arguments.length == args.length + 1 &&
            receiverKindMatches(m.arguments(0), receiverType)
        }
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
              TypeSubst.withCast(call, TypeSubst.result(method.returnType, classSubst, finalSubst))
            }
          }
        }
    }.nextOption()
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
  ): Seq[ExtensionMethodDefinition] = {
    val result = scala.collection.mutable.LinkedHashSet.empty[ExtensionMethodDefinition]

    def collect(currentType: ObjectType): Unit = {
      if (currentType == null) return
      currentType match {
        case classType: ClassType =>
          typing.lookupExtensionMethods(classType.name).foreach { extMethod =>
            if (extMethod.name == name) result += extMethod
          }
        case _: ArrayType =>
          typing.lookupExtensionMethods(Typing.ArrayExtensionReceiver).foreach { extMethod =>
            if (extMethod.name == name) result += extMethod
          }
        case _ =>
      }
      collect(currentType.superClass)
      currentType.interfaces.foreach(collect)
    }

    collect(targetType)
    result.toSeq
  }

  /** A builtin stdlib extension (the bundled collection/string utilities), as
   *  opposed to a user-declared `extension` block — used so a user extension can
   *  shadow a builtin of the same name instead of colliding as an ambiguity.
   *  Keep in sync with the container list in Typing.registerBuiltinExtensions. */
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
  /** Containers whose static helpers are registered as builtin extension methods
   *  (see Typing.registerBuiltinExtensions). A user-declared `extension` of the
   *  same name shadows these rather than colliding as an ambiguity. */
  val BuiltinExtensionContainers: Set[String] =
    Set("onion.Colls", "onion.Iterables", "onion.Maps", "onion.Strings", "onion.Sets", "onion.Hash", "onion.Codec", "onion.Text", "onion.Stats")
}
