package onion.tools.option

trait ParseResult {
  def getStatus: Int
}

/**
 * @author Kota Mizushima
 *         Date: 2005/04/08
 */
object ParseResult {
  final val SUCCEED: Int = 0
  final val FAILURE: Int = 1
}