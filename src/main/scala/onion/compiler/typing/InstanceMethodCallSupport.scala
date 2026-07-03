package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*
import onion.compiler.typing.session.TypingBodyContext

import java.util.{TreeSet => JTreeSet}

import ArgumentHelpers.hasNamedArguments

private[compiler] final class InstanceMethodCallSupport(
  bodyContext: TypingBodyContext,
  calls: MethodCallTyping,
  fallback: MethodCallFallbackSupport
) {
  private val overloadSupport = new CallOverloadSupport(calls.typing, calls)

  def typeMethodCall(node: AST.MethodCall, context: LocalContext, expected: Type = null): Option[Term] = {
    val target = calls.typed(node.target, context).getOrElse(null)
    if (target == null) return None

    val untypedClosureIndices = node.args.zipWithIndex.collect {
      case (expr, i) if isClosureWithUntypedParams(expr) => i
    }.toSet

    val params =
      if (untypedClosureIndices.isEmpty) calls.typedTerms(node.args.toArray, context)
      else null

    if (params == null && untypedClosureIndices.isEmpty) return None

    calls.normalizeMethodCallTarget(node, target).flatMap { resolved =>
      typeMethodCallOnObject(node, resolved.term, resolved.targetType, params, context, expected)
    }
  }

  def typeMethodCallOnObject(
    node: AST.MethodCall,
    target: Term,
    targetType: ObjectType,
    params: Array[Term],
    context: LocalContext,
    expected: Type = null
  ): Option[Term] = {
    val name = node.name

    if (hasNamedArguments(node.args)) {
      return typeMethodCallWithNamedArgs(node, target, targetType, context, expected)
    }

    // Zero-arg record copy is a full clone: p.copy()
    if (name == "copy" && node.args.isEmpty) {
      tryRecordCopy(node, target, targetType, context) match {
        case some @ Some(_) => return some
        case None =>
      }
    }

    if (params == null) {
      val untypedClosureIndices = node.args.zipWithIndex.collect {
        case (expr, i) if isClosureWithUntypedParams(expr) => i
      }.toSet
      return fallback.typeMethodCallWithBidirectionalInference(node, target, targetType, context, expected, untypedClosureIndices)
    }

    val methods = MethodResolution.findMethods(targetType, name, params, bodyContext.table)
    if (methods.length == 0) {
      val closureIndices = node.args.zipWithIndex.collect {
        case (expr, i) if expr.isInstanceOf[AST.ClosureExpression] => i
      }.toSet
      if (closureIndices.nonEmpty) {
        return fallback.typeMethodCallWithBidirectionalInference(node, target, targetType, context, expected, closureIndices)
      }
      // Resolution order for an unmatched call: extension methods first (a
      // user-declared extension wins), then a bean-property getter as a last
      // resort. The getter fallback lets `e.message()` resolve to getMessage() like
      // `e.message` does, so parens are optional on property accessors too.
      val ext = fallback.tryExtensionMethodCall(node, target, targetType, params, expected, reportIfNotFound = false)
      if (ext.isDefined) return ext
      if (params != null && params.length == 0) {
        val getters = {
          val g = MethodResolution.findMethods(targetType, calls.getter(name), params, bodyContext.table)
          if (g.length > 0) g else MethodResolution.findMethods(targetType, calls.getterBoolean(name), params, bodyContext.table)
        }
        if (getters.length > 0) {
          calls.selectSingleMethod(node, targetType, name, getters, calls.types(params)) match {
            case Some(getter) if (getter.modifier & AST.M_STATIC) == 0 =>
              val classSubst = TypeSubstitution.hierarchySubstitution(target.`type`, getter.affiliation)
              return calls.buildResolvedCall(node, getter, params, node.typeArgs, classSubst, expected)(
                expectedArgs => calls.prepareCallParams(node, node.args, getter, params, expectedArgs),
                finalParams => new Call(target, getter, finalParams)
              )
            case _ => // no usable getter
          }
        }
      }
      // Nothing matched: report not-found (extension fallback was silent above).
      calls.reportMethodNotFound(node, targetType, name, calls.types(params))
      return None
    }

    calls.selectSingleMethod(node, targetType, name, methods, calls.types(params)) match {
      case None => None
      case Some(method) if (method.modifier & AST.M_STATIC) != 0 =>
        calls.reportIllegalMethodCall(node, method, name)
        None
      case Some(method) =>
        val classSubst = TypeSubstitution.hierarchySubstitution(target.`type`, method.affiliation)
        calls.buildResolvedCall(node, method, params, node.typeArgs, classSubst, expected)(
          expectedArgs => calls.prepareCallParams(node, node.args, method, params, expectedArgs),
          finalParams => new Call(target, method, finalParams)
        )
    }
  }

  /**
   * Partial record copy with named arguments: p.copy(y = 9) fills the
   * unmentioned components from the receiver's getters.
   */
  private def tryRecordCopy(
    node: AST.MethodCall,
    target: Term,
    targetType: ObjectType,
    context: LocalContext
  ): Option[Term] = {
    if (node.name != "copy") return None
    val definition = targetType match {
      case d: ClassDefinition => d
      case applied: AppliedClassType =>
        applied.raw match {
          case d: ClassDefinition => d
          case _ => return None
        }
      case _ => return None
    }
    val components = definition.recordComponents.getOrElse(return None)

    val named = scala.collection.mutable.LinkedHashMap[String, AST.Expression]()
    node.args.foreach {
      case AST.NamedArgument(_, name, value) => named(name) = value
      case _ => return None // positional args mixed in: use the regular path
    }
    for (name <- named.keys if !components.exists(_._1 == name)) {
      calls.reportMethodNotFound(node, targetType, s"copy(${name} = ...)", Array[Type]())
      return Some(null).filter(_ != null) // reported; abort typing
    }

    val classSubst = TypeSubstitution.classSubstitution(targetType)
    val fullParams = new Array[Term](components.length)
    var i = 0
    while (i < components.length) {
      val (cname, ctype) = components(i)
      val expectedType = TypeSubstitution.substituteType(ctype, classSubst, scala.collection.immutable.Map.empty, defaultToBound = true)
      named.get(cname) match {
        case Some(expr) =>
          calls.typed(expr, context, expectedType) match {
            case Some(term) =>
              // Box primitives against reference-typed components so the
              // raw signature (copy(A, B)) still matches: copy(second = 42)
              fullParams(i) =
                if (!expectedType.isBasicType && term.isBasicType) onion.compiler.toolbox.Boxing.boxing(bodyContext.table, term)
                else term
            case None => return Some(null).filter(_ != null)
          }
        case None =>
          targetType.findMethod(cname, Array[Term]()) match {
            case Array(getter, _*) =>
              // Specialize the component type for applied records so the
              // kept value of first() on Pair[String, Integer] is a String
              val call = new Call(target, getter, Array[Term]())
              fullParams(i) = TypeSubst.withCast(call, TypeSubst.withClassOnly(getter.returnType, targetType))
            case _ => return None
          }
      }
      i += 1
    }

    targetType.findMethod("copy", fullParams) match {
      case Array(method, _*) =>
        calls.buildResolvedCall(node, method, fullParams, node.typeArgs, classSubst, null)(
          expectedArgs => calls.processParamsWithExpected(node, fullParams, expectedArgs),
          finalParams => new Call(target, method, finalParams)
        )
      case _ => None
    }
  }

  private def typeMethodCallWithNamedArgs(
    node: AST.MethodCall,
    target: Term,
    targetType: ObjectType,
    context: LocalContext,
    expected: Type
  ): Option[Term] = {
    val recordCopy = tryRecordCopy(node, target, targetType, context)
    if (recordCopy.isDefined) return recordCopy

    val candidates = new JTreeSet[Method](new MethodComparator)
    calls.collectMethodsMatching(targetType, node.name, candidates, calls.isInstanceMethod)
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
        val classSubst = TypeSubstitution.hierarchySubstitution(target.`type`, method.affiliation)
        calls.processNamedArguments(node, node.args, method, context).flatMap { params =>
          calls.buildResolvedCall(node, method, params, node.typeArgs, classSubst, expected)(
            expectedArgs => calls.processParamsWithExpected(node, params, expectedArgs),
            finalParams => new Call(target, method, finalParams)
          )
        }
    }
  }

  private def hasUntypedParams(closure: AST.ClosureExpression): Boolean =
    closure.args.exists(_.typeRef == null)

  private def isClosureWithUntypedParams(expr: AST.Expression): Boolean =
    expr match {
      case closure: AST.ClosureExpression => hasUntypedParams(closure)
      case _ => false
    }
}
