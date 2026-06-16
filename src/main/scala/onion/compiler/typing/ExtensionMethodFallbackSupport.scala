package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*

private[compiler] final class ExtensionMethodFallbackSupport(
  typing: Typing,
  calls: MethodCallTyping
) {
  def tryExtensionMethodCall(
    node: AST.MethodCall,
    target: Term,
    targetType: ObjectType,
    params: Array[Term],
    expected: Type
  ): Option[Term] = {
    selectApplicableExtensionMethod(targetType, node.name, params) match {
      case CandidateSelection.NoMatch =>
        calls.reportMethodNotFound(node, targetType, node.name, calls.types(params))
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

  private def selectApplicableExtensionMethod(
    targetType: ObjectType,
    name: String,
    params: Array[Term]
  ): CandidateSelection[ExtensionMethodDefinition] =
    targetType match {
      case _: ClassType =>
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

  private def buildExtensionCall(
    node: AST.MethodCall,
    target: Term,
    targetType: ObjectType,
    params: Array[Term],
    expected: Type,
    extMethod: ExtensionMethodDefinition
  ): Option[Term] = {
    val containerClass = extMethod.containerClass
    val staticArgs = Array(target) ++ params
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
      container.methods(name)
        .find { m =>
          Modifier.isStatic(m.modifier) && m.arguments.length == args.length + 1 &&
            receiverKindMatches(m.arguments(0), target.`type`)
        }
        .flatMap { method =>
          val classSubst = TypeSubstitution.classSubstitution(container)
          val knownStatic = (Array(target) ++ preliminaryParams).filter(_ != null)
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
            val staticArgs = Array(target) ++ finalArgs
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

  /** The backing static's first parameter must accept this receiver kind
   *  (array-taking overloads must not be picked for List receivers and
   *  vice versa). */
  private def receiverKindMatches(firstParam: Type, receiver: Type): Boolean =
    (firstParam, receiver) match {
      case (_: ArrayType, _: ArrayType) => true
      case (_: ArrayType, _) => false
      case (_, _: ArrayType) => false
      case _ => true
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

  /** A builtin stdlib extension (onion.Colls / onion.Iterables), as opposed to
   *  a user-declared `extension` block — used so a user extension can shadow a
   *  builtin of the same name instead of colliding as an ambiguity. */
  private def isBuiltinExtension(m: ExtensionMethodDefinition): Boolean = {
    val n = m.containerClass.name
    n == "onion.Colls" || n == "onion.Iterables"
  }

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
