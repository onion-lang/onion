package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for the "Regex" section of docs/reference/stdlib.md (and its
 * Japanese translation) against `onion.Regex`'s public API. The class has a
 * full set of `java.util.regex.Pattern`-typed overloads (added so `re"..."`
 * literals, which compile to `Pattern`, can be passed to the `Regex::`
 * helpers directly) that were never mentioned in either reference's Regex
 * section, making them undiscoverable without reading the Java source.
 */
class StdlibDocRegexModuleParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def sectionUnder(doc: String, heading: String): String = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == heading)
    assert(start >= 0, s"could not find heading '$heading' — the scan has rotted")
    lines.drop(start + 1).takeWhile(!_.trim.startsWith("## ")).mkString("\n")
  }

  private def subsectionUnder(section: String, subheading: String): String = {
    val lines = section.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == subheading)
    assert(start >= 0, s"could not find subheading '$subheading' — the scan has rotted")
    lines.drop(start + 1).takeWhile(!_.trim.startsWith("### ")).mkString("\n")
  }

  // Unique method names among the Pattern-typed overloads in
  // src/main/java/onion/Regex.java's "Pattern overloads" section.
  private val patternOverloadMethods = Seq(
    "matches", "find", "findAll", "findFirst",
    "groups", "groupsAll", "replace", "replaceFirst", "split"
  )

  it("documents the Pattern-literal Regex:: overloads in the English Regex section") {
    val regex = sectionUnder(read("docs/reference/stdlib.md"), "## Regex")
    val overloads = subsectionUnder(regex, "### Pattern literal overloads")
    patternOverloadMethods.foreach { name =>
      assert(overloads.contains(s"Regex::$name"),
        s"docs/reference/stdlib.md's Regex 'Pattern literal overloads' subsection doesn't " +
        s"mention Regex::$name, a public onion.Regex Pattern-typed overload")
    }
  }

  it("documents the Pattern-literal Regex:: overloads in the Japanese Regex section") {
    val regex = sectionUnder(read("docs/ja/reference/stdlib.md"), "## Regex")
    val overloads = subsectionUnder(regex, "### Pattern リテラルのオーバーロード")
    patternOverloadMethods.foreach { name =>
      assert(overloads.contains(s"Regex::$name"),
        s"docs/ja/reference/stdlib.md's Regex 'Pattern リテラルのオーバーロード' subsection doesn't " +
        s"mention Regex::$name, a public onion.Regex Pattern-typed overload")
    }
  }
}
