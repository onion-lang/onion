package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*

import java.util.{TreeSet => JTreeSet}

import scala.jdk.CollectionConverters.*

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
    candidates.flatMap { method =>
      val classSubst = TypeSubstitution.classSubstitution(typeRef)
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
          Some(GenericMethodTypeArguments.infer(typing, node, method, params, classSubst, expected))
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

    candidates.flatMap { method =>
      val methodArgCount = method.arguments.length
      val countOk =
        if (method.isVararg) argsCount >= method.minArguments
        else argsCount >= method.minArguments && argsCount <= methodArgCount

      if (!countOk) None
      else {
        val classSubst = TypeSubstitution.classSubstitution(receiverType)
        val methodSubst = GenericMethodTypeArguments.infer(typing, node, method, knownParams, classSubst, expected)
        val expectedArgs = TypeSubst.args(method, classSubst, methodSubst)
        Option.when(
          knownParamIndices.forall { case (index, term) =>
            isApplicableAtIndex(method, expectedArgs, index, term.`type`)
          }
        )(ApplicableMethod(method, expectedArgs, methodSubst))
      }
    }.toList
  }

  def selectMostSpecificApplicable(
    applicable: List[ApplicableMethod],
    relevantIndices: Seq[Int] = Nil
  ): CandidateSelection[ApplicableMethod] = {
    if (applicable.isEmpty) CandidateSelection.NoMatch
    else if (applicable.length == 1) CandidateSelection.Selected(applicable.head)
    else {
      def compareApplicable(a1: ApplicableMethod, a2: ApplicableMethod): Int =
        if isMoreSpecific(a1.expectedArgs, a2.expectedArgs, relevantIndices) then -1
        else if isMoreSpecific(a2.expectedArgs, a1.expectedArgs, relevantIndices) then 1
        else 0

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
      val castType = TypeSubst(method.returnType, classSubst, methodSubst)
      TypeSubst.withCast(call, castType)
    }

  private def isStaticApplicable(
    method: Method,
    expectedArgs: Array[Type],
    params: Array[Term]
  ): Boolean =
    if (method.isVararg && expectedArgs.nonEmpty) {
      val fixedArgCount = expectedArgs.length - 1
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
      params.indices.forall(i => isApplicableAtIndex(method, expectedArgs, i, params(i).`type`))
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
