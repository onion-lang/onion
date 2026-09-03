package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `Json` module docs.
 *
 * `onion.Json::stringify` is overloaded with a pretty-printing sibling,
 * `stringifyPretty`, that indents the output. docs/reference/stdlib.md shows
 * both (`Json::stringify(obj) / Json::stringifyPretty(obj)`), but its Japanese
 * translation only ever showed the plain `Json::stringify(m)` call -- never
 * `stringifyPretty` at all.
 */
class JsonDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def hasStringifyPretty(doc: String): Boolean =
    doc.contains("Json::stringifyPretty")

  it("actual onion.Json exposes stringifyPretty (sanity check)") {
    val hasStringifyPretty = classOf[onion.Json].getDeclaredMethods.exists(_.getName == "stringifyPretty")
    assert(hasStringifyPretty, "reflection on onion.Json found no stringifyPretty method -- the scan has rotted")
  }

  it("docs/reference/stdlib.md documents Json::stringifyPretty") {
    assert(hasStringifyPretty(read("docs/reference/stdlib.md")),
      "docs/reference/stdlib.md never shows Json::stringifyPretty")
  }

  it("docs/ja/reference/stdlib.md documents Json::stringifyPretty") {
    assert(hasStringifyPretty(read("docs/ja/reference/stdlib.md")),
      "docs/ja/reference/stdlib.md never shows Json::stringifyPretty")
  }
}
