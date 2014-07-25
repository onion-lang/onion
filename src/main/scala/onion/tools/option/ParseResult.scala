package onion.tools.option
import scala.collection.mutable.{Map, Seq}

sealed trait ParseResult {
  def status: Int
}

case class ParseSuccess(options: Map[String, CommandLineParam], arguments: Array[String]) extends ParseResult {
  def status: Int = ParseResult.SUCCEED
}

case class ParseFailure(lackedOptions: Array[ValuedParam], invalidOptions: Array[ValuedParam]) extends ParseResult {
  def status: Int = ParseResult.FAILURE
}

object ParseResult {
  final val SUCCEED: Int = 0
  final val FAILURE: Int = 1
}