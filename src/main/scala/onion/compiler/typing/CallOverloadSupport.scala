package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*

import java.util.{TreeSet => JTreeSet}


import ArgumentHelpers.{extractNamedArgInfo, filterByNamedArgs, fillDefaultArguments}

private[typing] enum CandidateSelection[+A] {
  case NoMatch
  case Ambiguous(first: A, second: A)
  case Selected(value: A)
}

private[typing] final case class ApplicableMethod(
  method: Method,
  expectedArgs: Array[Type],
  methodSubst: scala.collection.immutable.Map[String, Type]
)

private[compiler] final class CallOverloadSupport(
  typing: Typing,
  calls: MethodCallTyping
) {
  def selectNamedArgumentMethod(
    candidates: JTreeSet[Method],
    args: List[AST.Expression]
  ): CandidateSelection[Method] = {
    val info = extractNamedArgInfo(args)
    val applicable = filterByNamedArgs(candidates, info)

    if (applicable.isEmpty) CandidateSelection.NoMatch
    else if (applicable.length > 1) CandidateSelection.Ambiguous(applicable(0), applicable(1))
    else CandidateSelection.Selected(applicable.head)
  }

  def collectStaticApplicables(
    typeRef: ClassType,
    candidates: Iterable[Method],
    node: AST.Node,
    params: Array[Term],
    expected: Type,
    mappedTypeArgs: Option[Array[Type]] = None,
    reportExplicitTypeArgErrors: Boolean = false
  ): List[ApplicableMethod] =
    val classSubst = TypeSubstitution.classSubstitution(typeRef) // same for every candidate
    candidates.flatMap { method =>
      val methodSubstOpt = mappedTypeArgs match {
        case Some(mapped) =>
          GenericMethodTypeArguments.explicitFromMappedArgs(
            typing,
            node,
            method,
            mapped,
            classSubst,
            reportErrors = reportExplicitTypeArgErrors
          )
        case None =>
          // Applicability collection over all candidates: suppress bound-check
          // errors so a discarded bounded overload cannot leak its constraint
          // onto the call (issue #298). The selected method is re-inferred with
          // errors on in buildResolvedCall/resolveMethodTypeArgs.
          Some(GenericMethodTypeArguments.infer(typing, node, method, params, classSubst, expected, reportErrors = false))
      }
      methodSubstOpt.flatMap { methodSubst =>
        val expectedArgs = TypeSubst.args(method, classSubst, methodSubst)
        Option.when(isStaticApplicable(method, expectedArgs, params))(
          ApplicableMethod(method, expectedArgs, methodSubst)
        )
      }
    }.toList

  def collectPartialInstanceApplicables(
    receiverType: Type,
    candidates: Iterable[Method],
    node: AST.Node,
    params: Array[Term],
    expected: Type
  ): List[ApplicableMethod] = {
    val knownParams = params.filter(_ != null)
    val knownParamIndices = params.zipWithIndex.collect {
      case (term, index) if term != null => (index, term)
    }
    val argsCount = params.length

    val classSubst = TypeSubstitution.classSubstitution(receiverType) // same for every candidate
    candidates.flatMap { method =>
      val methodArgCount = method.argumentCount
      val countOk =
        if (method.isVararg) argsCount >= method.minArguments
        else argsCount >= method.minArguments && argsCount <= methodArgCount

      if (!countOk) None
      else {
        // Applicability collection: suppress bound errors so a discarded bounded
        // overload cannot leak its constraint onto the call (issue #298).
        val methodSubst = GenericMethodTypeArguments.infer(typing, node, method, knownParams, classSubst, expected, reportErrors = false)
        val expectedArgs = TypeSubst.args(method, classSubst, methodSubst)
        Option.when(
          knownParamIndices.forall { case (index, term) =>
            isApplicableAtIndex(method, expectedArgs, index, term.`type`) ||
              (index < expectedArgs.length && TypeRules.emptyCollectionLiteralAccepts(expectedArgs(index), term))
          }
        )(ApplicableMethod(method, expectedArgs, methodSubst))
      }
    }.toList
  }

  /**
   * SAM overload disambiguation: at a closure-argument position, drop candidates
   * whose functional-interface parameter is value/void incompatible with the
   * lambda body (e.g. `submit(() -> 42)` keeps submit(Callable), drops
   * submit(Runnable)). `matchesSam(i, samParamType)` is Some(true/false) when the
   * compatibility is known and None when it is not (then the candidate is kept).
   * Returns the original list unchanged unless filtering leaves a smaller,
   * non-empty set.
   */
  def disambiguateClosureOverloads(
    applicable: List[ApplicableMethod],
    closureIndices: Set[Int],
    matchesSam: (Int, Type) => Option[Boolean]
  ): List[ApplicableMethod] = {
    if (applicable.length <= 1 || closureIndices.isEmpty) return applicable
    val filtered = applicable.filter { am =>
      closureIndices.forall { i =>
        if (i >= am.expectedArgs.length) true
        else matchesSam(i, am.expectedArgs(i)) match {
          case Some(compatible) => compatible
          case None => true
        }
      }
    }
    if (filtered.nonEmpty && filtered.length < applicable.length) filtered else applicable
  }

  def selectMostSpecificApplicable(
    applicable0: List[ApplicableMethod],
    relevantIndices: Seq[Int] = Nil,
    argTypes: Array[Type] = null
  ): CandidateSelection[ApplicableMethod] = {
    // Java-style phasing: varargs are only considered when no fixed-arity
    // method is applicable (List.of(1,2,3) prefers of(E,E,E) over of(E...))
    val applicable =
      if (applicable0.exists(a => !a.method.isVararg)) applicable0.filter(a => !a.method.isVararg)
      else applicable0
    if (applicable.isEmpty) CandidateSelection.NoMatch
    else if (applicable.length == 1) CandidateSelection.Selected(applicable.head)
    else {
      def compareApplicable(a1: ApplicableMethod, a2: ApplicableMethod): Int =
        if isMoreSpecific(a1.expectedArgs, a2.expectedArgs, relevantIndices) then -1
        else if isMoreSpecific(a2.expectedArgs, a1.expectedArgs, relevantIndices) then 1
        else boxingTiebreak(a1.expectedArgs, a2.expectedArgs, argTypes)

      val sorted = applicable.sortWith((a1, a2) => compareApplicable(a1, a2) < 0)
      if (sorted.length >= 2 && compareApplicable(sorted.head, sorted(1)) >= 0)
        CandidateSelection.Ambiguous(sorted.head, sorted(1))
      else
        CandidateSelection.Selected(sorted.head)
    }
  }

  def buildStaticCall(
    typeRef: ClassType,
    method: Method,
    params: Array[Term],
    classSubst: scala.collection.immutable.Map[String, Type],
    methodSubst: scala.collection.immutable.Map[String, Type]
  ): Option[Term] =
    fillDefaultArguments(params, method).map { finalParams =>
      val call = new CallStatic(typeRef, method, finalParams)
      val castType = TypeSubst.result(method.returnType, classSubst, methodSubst)
      TypeSubst.withCast(call, castType)
    }

  private def isStaticApplicable(
    method: Method,
    expectedArgs: Array[Type],
    params: Array[Term]
  ): Boolean =
    if (method.isVararg && expectedArgs.nonEmpty) {
      val fixedArgCount = expectedArgs.length - 1
      if (!expectedArgs.last.isArrayType) return false
      val varargType = expectedArgs.last.asInstanceOf[ArrayType]
      val componentType = varargType.base

      if (params.length < fixedArgCount) false
      else {
        val fixedMatch = (0 until fixedArgCount).forall { i =>
          calls.isAssignableWithBoxing(expectedArgs(i), params(i).`type`)
        }
        val varargMatch =
          if (params.length == expectedArgs.length) {
            val lastParamType = params.last.`type`
            (lastParamType.isArrayType && TypeRules.isSuperType(varargType, lastParamType)) ||
              calls.isAssignableWithBoxing(componentType, lastParamType)
          } else if (params.length > expectedArgs.length) {
            (fixedArgCount until params.length).forall { i =>
              calls.isAssignableWithBoxing(componentType, params(i).`type`)
            }
          } else {
            true
          }
        fixedMatch && varargMatch
      }
    } else {
      params.length >= method.minArguments &&
      params.length <= expectedArgs.length &&
      params.indices.forall(i =>
        isApplicableAtIndex(method, expectedArgs, i, params(i).`type`) ||
          TypeRules.emptyCollectionLiteralAccepts(expectedArgs(i), params(i)))
    }

  private def isApplicableAtIndex(
    method: Method,
    expectedArgs: Array[Type],
    index: Int,
    actualType: Type
  ): Boolean =
    if (!method.isVararg || expectedArgs.isEmpty) {
      index < expectedArgs.length && calls.isAssignableWithBoxing(expectedArgs(index), actualType)
    } else {
      val fixedArgCount = expectedArgs.length - 1
      if (index < fixedArgCount) {
        calls.isAssignableWithBoxing(expectedArgs(index), actualType)
      } else {
        if (!expectedArgs.last.isArrayType) return false
        val varargType = expectedArgs.last.asInstanceOf[ArrayType]
        val componentType = varargType.base
        if (index == fixedArgCount) {
          (actualType.isArrayType && TypeRules.isSuperType(varargType, actualType)) ||
            calls.isAssignableWithBoxing(componentType, actualType)
        } else {
          calls.isAssignableWithBoxing(componentType, actualType)
        }
      }
    }

  /**
   * Java's phase-1 preference for incomparable int/reference overloads, by the actual
   * argument types: a primitive argument favours the formal that is that same primitive,
   * a boxed one the reference formal it reaches without unboxing (`valueOf(Object)` over
   * `valueOf(int)` for an Integer). The instance-call path applies the same rule in
   * MethodResolutionSupport. 0 when there are no argument types or the positions disagree.
   */
  private def boxingTiebreak(a1: Array[Type], a2: Array[Type], argTypes: Array[Type]): Int = {
    if (argTypes == null || a1.length != argTypes.length || a2.length != argTypes.length) return 0
    var oneBetter = false
    var twoBetter = false
    argTypes.indices.foreach { i =>
      val actual = argTypes(i)
      val (f1, f2) = (a1(i), a2(i))
      if (actual != null && actual.isBasicType) {
        if ((f1 eq actual) && !(f2 eq actual) && !f2.isBasicType) oneBetter = true
        else if ((f2 eq actual) && !(f1 eq actual) && !f1.isBasicType) twoBetter = true
      } else if (actual != null && !actual.isNullType && !actual.isBottomType) {
        if (!f1.isBasicType && f2.isBasicType) oneBetter = true
        else if (!f2.isBasicType && f1.isBasicType) twoBetter = true
      }
    }
    if (oneBetter && !twoBetter) -1 else if (twoBetter && !oneBetter) 1 else 0
  }

  private def isMoreSpecific(
    candidate: Array[Type],
    baseline: Array[Type],
    relevantIndices: Seq[Int]
  ): Boolean =
    if (relevantIndices.nonEmpty) {
      relevantIndices.forall { index =>
        index < candidate.length &&
        index < baseline.length &&
        TypeRules.isSuperType(baseline(index), candidate(index))
      }
    } else {
      TypeRules.isAllSuperType(baseline, candidate)
    }
}
