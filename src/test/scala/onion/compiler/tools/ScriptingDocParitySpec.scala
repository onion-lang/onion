package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for docs/guide/scripting.md against its Japanese translation,
 * docs/ja/guide/scripting.md, which was missing the "Running Scripts",
 * "Command-Line Arguments", "Running External Commands", "Files and Globs",
 * "JSON and HTTP", and "Putting It Together" sections entirely — only the
 * back half of the English page (from "Scheme-Prefixed Literals" onward) had
 * ever been translated.
 */
class ScriptingDocParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def apiTokens(doc: String): Set[String] =
    """(Args|Proc|Files|Http|Json)::\w+""".r.findAllMatchIn(doc).map(_.group(0)).toSet

  it("mentions the same stdlib API calls as the English scripting guide") {
    val en = apiTokens(read("docs/guide/scripting.md"))
    val ja = apiTokens(read("docs/ja/guide/scripting.md"))
    assert(en.nonEmpty, "docs/guide/scripting.md listed no Args/Proc/Files/Http/Json calls — the scan has rotted")
    val missing = en -- ja
    assert(missing.isEmpty,
      s"docs/ja/guide/scripting.md is missing ${missing.size} stdlib call(s) present in English: " +
      missing.toSeq.sorted.mkString(", "))
  }
}
