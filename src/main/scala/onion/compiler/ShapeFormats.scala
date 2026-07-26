package onion.compiler

/**
 * The document formats a `shape name = <format>` clause understands.
 *
 * One list, so the parser, the desugaring and the diagnostic that reports an unknown name
 * cannot drift apart -- the mistake `derive!` made, whose marker whitelist was written out
 * in `TypingOutlinePass` and its message text separately in the properties file.
 */
private[compiler] object ShapeFormats {

  /** Format name -> the `onion.Shapes` factory that builds it. */
  val all: List[(String, String)] = List(
    "json" -> "json",
    "yaml" -> "yaml",
    "config" -> "config"
  )

  def factoryFor(format: String): Option[String] =
    all.collectFirst { case (name, factory) if name.equalsIgnoreCase(format) => factory }

  def isSupported(format: String): Boolean = factoryFor(format).isDefined

  def supportedNames: String = all.map(_._1).mkString(", ")
}
