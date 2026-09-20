package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for CLAUDE.md's "### Advanced Features" section against its Japanese
 * translation in docs/ja/CLAUDE_ja.md's "### 高度な機能" section. CLAUDE.md documents four
 * example blocks there — Do Notation, Shape-First Scripting (scheme literals, regex select
 * patterns, `from re"..."` records, the `|>` pipeline, and auto-CLI), Asynchronous
 * Programming, and Try-Catch — each backed by real, tested behavior (SchemeLiteralSpec,
 * RecordFromRegexSpec, PipelineOperatorSpec, AutoCliSpec, ...). docs/ja/CLAUDE_ja.md's
 * section jumped straight from the do-notation example to Asynchronous Programming,
 * silently omitting the entire Shape-First Scripting block.
 */
class ClaudeMdAdvancedFeaturesScriptingParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def sectionUnder(doc: String, heading: String): String = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == heading)
    assert(start >= 0, s"could not find heading '$heading' — the scan has rotted")
    lines.drop(start + 1)
      .takeWhile(l => !l.trim.startsWith("### ") && !l.trim.startsWith("## "))
      .mkString("\n")
  }

  it("documents Shape-First Scripting (scheme literals, from-regex records, pipeline, auto-CLI) in CLAUDE.md") {
    val section = sectionUnder(read("CLAUDE.md"), "### Advanced Features")
    assert(section.contains("Shape-First Scripting"),
      "CLAUDE.md's Advanced Features section doesn't mention Shape-First Scripting")
    assert(section.contains("re\""), "CLAUDE.md's Advanced Features section doesn't show a re\"...\" scheme literal")
    assert(section.contains("from re\""), "CLAUDE.md's Advanced Features section doesn't show a `from re\"...\"` pattern-attached record")
    assert(section.contains("|>"), "CLAUDE.md's Advanced Features section doesn't show the |> pipeline operator")
    assert(section.contains("auto-CLI"), "CLAUDE.md's Advanced Features section doesn't mention auto-CLI")
  }

  it("documents the same Shape-First Scripting content in docs/ja/CLAUDE_ja.md's 高度な機能 section") {
    val section = sectionUnder(read("docs/ja/CLAUDE_ja.md"), "### 高度な機能")
    assert(section.contains("re\""),
      "docs/ja/CLAUDE_ja.md's 高度な機能 section doesn't show a re\"...\" scheme literal — " +
      "the Shape-First Scripting block present in CLAUDE.md appears to be missing")
    assert(section.contains("from re\""),
      "docs/ja/CLAUDE_ja.md's 高度な機能 section doesn't show a `from re\"...\"` pattern-attached record")
    assert(section.contains("|>"),
      "docs/ja/CLAUDE_ja.md's 高度な機能 section doesn't show the |> pipeline operator")
    assert(section.contains("auto-CLI"),
      "docs/ja/CLAUDE_ja.md's 高度な機能 section doesn't mention auto-CLI")
  }
}
