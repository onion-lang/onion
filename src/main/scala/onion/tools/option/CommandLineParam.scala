package onion.tools.option

/**
 * @author Kota Mizushima
 */
sealed trait CommandLineParam
case class ValuedParam(value: String) extends CommandLineParam
case object NoValuedParam extends CommandLineParam
