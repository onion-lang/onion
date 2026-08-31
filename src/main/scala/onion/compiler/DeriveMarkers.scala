package onion.compiler

/**
 * The `derive!(...)` markers a record can request.
 *
 * One list, so the marker check in `TypingOutlinePass` and the diagnostic that
 * reports an unknown marker cannot drift apart -- the same fix `ShapeFormats` made
 * for `shape name = <format>`.
 */
private[compiler] object DeriveMarkers {

  val all: List[String] = List("Json", "Yaml")

  def isSupported(marker: String): Boolean = all.contains(marker)

  def supportedNames: String = all.mkString(", ")
}
