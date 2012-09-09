package onion.tools.option
import java.util.{List, Map}

sealed trait ParseResult {
  def getStatus: Int
}

case class ParseSuccess(noArgumentOptions: Map[_, _], options: Map[_, _], arguments: List[_]) extends ParseResult {
  def getStatus: Int = ParseResult.SUCCEED

  def getNoArgumentOptions: Map[_, _] = noArgumentOptions

  def getOptions: Map[_, _] = options

  def getArguments: List[_] = arguments
}

case class ParseFailure(lackedOptions: Array[String], invalidOptions: Array[String]) extends ParseResult {
  def getStatus: Int = ParseResult.FAILURE

  def getLackedOptions: Array[String] = lackedOptions

  def getInvalidOptions: Array[String] = invalidOptions
}

object ParseResult {
  final val SUCCEED: Int = 0
  final val FAILURE: Int = 1
}