package onion.tools.option
import java.util.{List, Map}

sealed trait ParseResult {
  def status: Int
}

case class ParseSuccess(noArgumentOptions: Map[_, _], options: Map[_, _], arguments: List[_]) extends ParseResult {
  def status: Int = ParseResult.SUCCEED
}

case class ParseFailure(lackedOptions: Array[String], invalidOptions: Array[String]) extends ParseResult {
  def status: Int = ParseResult.FAILURE
}

object ParseResult {
  final val SUCCEED: Int = 0
  final val FAILURE: Int = 1
}