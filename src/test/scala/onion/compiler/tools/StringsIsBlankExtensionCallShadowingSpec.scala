package onion.compiler.tools

import onion.tools.Shell

/**
 * `onion.Strings` declares `isBlank(String)`, but `java.lang.String` already
 * defines an instance method with the same name (since Java 11), and an
 * instance method always wins over an extension method of the same name --
 * the same hazard `StringsExtensionCallShadowingSpec` documents for
 * `split`/`substring`/`lines`/`chars`/`repeat` and
 * `StringsContainsIsEmptyExtensionCallShadowingSpec` documents for
 * `contains`/`isEmpty`.
 *
 * Unlike `contains`/`isEmpty`, this disagreement is observable on an
 * ordinary non-null `String` -- no platform-typed `null` required.
 * `onion.Strings::isBlank` is implemented as `str.trim().isEmpty()`, and
 * `String::trim` only strips characters `<= U+0020`. Native
 * `String::isBlank` instead treats every character satisfying
 * `Character.isWhitespace` as blank, which includes Unicode space
 * separators like EM SPACE (U+2003) that `trim()` does not strip. So a
 * string made up only of an EM SPACE is blank under the native method that
 * extension-call syntax reaches, but not blank under `onion.Strings`'s
 * static form.
 *
 * Locks in the shadowing (a future reordering of `BuiltinExtensionContainers`
 * or the resolution rule wouldn't be caught by unit tests exercising only
 * ASCII-whitespace strings), and checks that both docs carry the warning
 * (docs/reference/stdlib.md and its Japanese translation, Strings Module
 * section).
 */
class StringsIsBlankExtensionCallShadowingSpec extends AbstractShellSpec {

  // U+2003 EM SPACE, embedded literally (not as a \u escape) to sidestep the
  // Java/Scala-style unicode-escape pretokenizing that both the host Scala
  // source and the generated Onion source would otherwise be subject to.
  private val emSpace = " "

  private def runBool(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): Boolean {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "StringsIsBlankShadow.on", Array()))
  }

  describe("extension-call syntax for isBlank() on an EM-SPACE-only String shadows onion.Strings") {
    it("s.isBlank() on a string containing only an EM SPACE (U+2003) reaches native String.isBlank and returns true") {
      runBool(s"""val s: String = " $emSpace "\n    return s.isBlank()""", Shell.Success(true))
    }

    it("Strings::isBlank(s) on the same string is trim()-based and returns false instead") {
      runBool(s"""val s: String = " $emSpace "\n    return Strings::isBlank(s)""", Shell.Success(false))
    }

    it("for an ASCII-whitespace-only string, extension-call and static-call syntax agree") {
      runBool("return \"   \".isBlank()", Shell.Success(true))
      runBool("return Strings::isBlank(\"   \")", Shell.Success(true))
    }

    it("for a non-blank string, extension-call and static-call syntax agree") {
      runBool("return \"hi\".isBlank()", Shell.Success(false))
      runBool("return Strings::isBlank(\"hi\")", Shell.Success(false))
    }
  }

  describe("docs carry the isBlank() shadowing warning in the Strings Module section") {
    def read(p: String): String =
      java.nio.file.Files.readString(java.nio.file.Path.of(p))

    def section(doc: String, heading: String): String = {
      val lines = doc.linesIterator.toIndexedSeq
      val start = lines.indexWhere(_.contains(heading))
      assert(start >= 0, s"""could not find a "$heading" heading -- the scan has rotted""")
      val rest = lines.drop(start + 1)
      val end = rest.indexWhere(_.startsWith("## "))
      (if (end < 0) rest else rest.take(end)).mkString("\n")
    }

    val enMarkers =
      Set("isBlank", "trim", "U+2003", "EM SPACE")

    val jaMarkers =
      Set("isBlank", "trim", "U+2003")

    it("docs/reference/stdlib.md's Strings Module section warns about isBlank() shadowing") {
      val doc = section(read("docs/reference/stdlib.md"), "## Strings Module")
      val missing = enMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/reference/stdlib.md's Strings Module section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }

    it("docs/ja/reference/stdlib.md's Strings section warns about isBlank() shadowing") {
      val doc = section(read("docs/ja/reference/stdlib.md"), "## Strings モジュール")
      val missing = jaMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/ja/reference/stdlib.md's Strings section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }
  }
}
