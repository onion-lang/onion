package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.typing.session.TypingBodyContext

import ArgumentHelpers.fillDefaultArguments

private[compiler] final class CallArgumentTypingSupport(
  bodyContext: TypingBodyContext,
  typed: (AST.Expression, LocalContext, Type) => Option[Term],
  processAssignable: (AST.Node, Type, Term) => Term
) {
  def processParamsWithExpected(
    node: AST.Node,
    params: Array[Term],
    expectedArgs: Array[Type]
  ): Option[Array[Term]] = {
    val results = params.zipWithIndex.map { case (param, i) =>
      processAssignable(node, expectedArgs(i), param)
    }
    if (results.contains(null)) None else Some(results)
  }

  def processParamsWithArgs(
    args: Seq[AST.Expression],
    params: Array[Term],
    expectedArgs: Array[Type]
  ): Option[Array[Term]] = {
    val results = params.zipWithIndex.map { case (param, i) =>
      processAssignable(args(i), expectedArgs(i), param)
    }
    if (results.contains(null)) None else Some(results)
  }

  def wrapVarargParams(
    method: Method,
    params: Array[Term],
    expectedArgs: Array[Type]
  ): Array[Term] = {
    if (!method.isVararg || expectedArgs.isEmpty) return params

    val fixedArgCount = expectedArgs.length - 1
    val varargType = expectedArgs.last.asInstanceOf[ArrayType]

    if (params.length == expectedArgs.length) {
      val lastParamType = params.last.`type`
      if (lastParamType.isArrayType && TypeRules.isSuperType(varargType, lastParamType)) {
        return params
      }
    }

    val fixedParams = params.take(fixedArgCount)
    val varargElements = params.drop(fixedArgCount)
    val arrayTerm = new NewArrayWithValues(varargType, varargElements)
    fixedParams :+ arrayTerm
  }

  private def processVarargParamsWithExpected(
    node: AST.Node,
    method: Method,
    params: Array[Term],
    expectedArgs: Array[Type]
  ): Option[Array[Term]] = {
    if (!method.isVararg || expectedArgs.isEmpty) {
      return processParamsWithExpected(node, params, expectedArgs)
    }

    val fixedArgCount = expectedArgs.length - 1
    val varargType = expectedArgs.last.asInstanceOf[ArrayType]
    val componentType = varargType.base

    val fixedResults = (0 until fixedArgCount).map { i =>
      if (i < params.length) processAssignable(node, expectedArgs(i), params(i)) else null
    }.toArray

    if (fixedResults.contains(null)) return None

    if (params.length == expectedArgs.length) {
      val lastParam = params.last
      val lastParamType = lastParam.`type`
      if (lastParamType.isArrayType && TypeRules.isSuperType(varargType, lastParamType)) {
        val processedLast = processAssignable(node, varargType, lastParam)
        if (processedLast == null) return None
        return Some(fixedResults :+ processedLast)
      }
    }

    val varargElements = params.drop(fixedArgCount).map { param =>
      processAssignable(node, componentType, param)
    }
    if (varargElements.contains(null)) return None

    val arrayTerm = new NewArrayWithValues(varargType, varargElements)
    Some(fixedResults :+ arrayTerm)
  }

  def prepareCallParams(
    node: AST.Node,
    args: Seq[AST.Expression],
    method: Method,
    params: Array[Term],
    expectedArgs: Array[Type]
  ): Option[Array[Term]] = {
    val processedOpt =
      if (method.isVararg) {
        processVarargParamsWithExpected(node, method, params, expectedArgs)
      } else {
        processParamsWithArgs(args, params, expectedArgs)
      }

    processedOpt.flatMap { processedParams =>
      if (method.isVararg) Some(processedParams)
      else fillDefaultArguments(processedParams, method)
    }
  }

  def processNamedArguments(
    node: AST.Node,
    args: List[AST.Expression],
    method: Method,
    context: LocalContext
  ): Option[Array[Term]] = {
    val argsWithDefaults = method.argumentsWithDefaults
    val paramNames = argsWithDefaults.map(_.name)
    val result = new Array[Term](argsWithDefaults.length)
    val filled = new Array[Boolean](argsWithDefaults.length)

    var positionalIndex = 0
    var sawNamed = false
    var hasError = false

    args.foreach { arg =>
      arg match {
        case named: AST.NamedArgument =>
          sawNamed = true
          val paramIndex = paramNames.indexOf(named.name)
          if (paramIndex < 0) {
            bodyContext.report(UNKNOWN_PARAMETER_NAME, named, named.name)
            hasError = true
          } else if (filled(paramIndex)) {
            bodyContext.report(DUPLICATE_ARGUMENT, named, named.name)
            hasError = true
          } else {
            typed(named.value, context, null) match {
              case Some(term) =>
                result(paramIndex) = term
                filled(paramIndex) = true
              case None =>
                hasError = true
            }
          }

        case expr =>
          if (sawNamed) {
            bodyContext.report(POSITIONAL_AFTER_NAMED, expr)
            hasError = true
          } else if (positionalIndex >= argsWithDefaults.length) {
            typed(expr, context, null)
            positionalIndex += 1
          } else {
            typed(expr, context, null) match {
              case Some(term) =>
                result(positionalIndex) = term
                filled(positionalIndex) = true
                positionalIndex += 1
              case None =>
                hasError = true
                positionalIndex += 1
            }
          }
      }
    }

    if (hasError) return None

    val missingRequired = argsWithDefaults.indices.find(i => !filled(i) && argsWithDefaults(i).defaultValue.isEmpty)
    if (missingRequired.isDefined) {
      bodyContext.report(METHOD_NOT_FOUND, node, method.affiliation, method.name, argsWithDefaults.map(_.argType))
      return None
    }

    argsWithDefaults.indices.foreach { i =>
      if (!filled(i)) {
        result(i) = argsWithDefaults(i).defaultValue.get
        filled(i) = true
      }
    }

    Some(result)
  }
}
