package onion.compiler.typing

import onion.compiler.AST
import onion.compiler.TypedAST.{Method, Term}

import java.util.{TreeSet => JTreeSet}
import scala.jdk.CollectionConverters.*

/**
 * Helper utilities for argument processing in method calls.
 *
 * Contains pure functions for:
 * - Named argument analysis
 * - Default argument filling
 * - Argument filtering
 */
private[typing] object ArgumentHelpers {

  /** Information about named arguments in an argument list */
  case class NamedArgInfo(namedArgNames: Set[String], positionalCount: Int)

  /**
   * Extract named argument information from expression list.
   *
   * @param args List of argument expressions
   * @return NamedArgInfo containing named argument names and positional count
   */
  def extractNamedArgInfo(args: Seq[AST.Expression]): NamedArgInfo = {
    val namedArgNames = args.collect { case na: AST.NamedArgument => na.name }.toSet
    val positionalCount = args.takeWhile(!_.isInstanceOf[AST.NamedArgument]).size
    NamedArgInfo(namedArgNames, positionalCount)
  }

  /**
   * Check if an argument list contains any named arguments.
   *
   * @param args List of argument expressions
   * @return true if any named arguments are present
   */
  def hasNamedArguments(args: List[AST.Expression]): Boolean =
    args.exists(_.isInstanceOf[AST.NamedArgument])

  /**
   * Filter methods by named argument compatibility.
   *
   * A method is compatible if:
   * 1. All named argument names are valid parameter names
   * 2. The positional argument count doesn't exceed the parameter count
   *
   * @param candidates Set of candidate methods
   * @param info Named argument information
   * @return List of compatible methods
   */
  def filterByNamedArgs(candidates: JTreeSet[Method], info: NamedArgInfo): List[Method] =
    candidates.asScala.filter { method =>
      val paramNames = method.argumentsWithDefaults.map(_.name).toSet
      info.namedArgNames.subsetOf(paramNames) && info.positionalCount <= method.arguments.length
    }.toList

  /**
   * Fill missing arguments with default values.
   *
   * If params array is smaller than the method's parameter count,
   * the remaining slots are filled with default values.
   *
   * @param params Partially filled parameter array
   * @param method Method with default arguments
   * @return Some(complete array) if all missing params have defaults, None otherwise
   */
  def fillDefaultArguments(params: Array[Term], method: Method): Option[Array[Term]] = {
    val argsWithDefaults = method.argumentsWithDefaults
    if (params.length >= argsWithDefaults.length) {
      Some(params)
    } else {
      val result = new Array[Term](argsWithDefaults.length)
      System.arraycopy(params, 0, result, 0, params.length)
      var i = params.length
      while (i < argsWithDefaults.length) {
        argsWithDefaults(i).defaultValue match {
          case Some(term) => result(i) = term
          case None => return None
        }
        i += 1
      }
      Some(result)
    }
  }
}
