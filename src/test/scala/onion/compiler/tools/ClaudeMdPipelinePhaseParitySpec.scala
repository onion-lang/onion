package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for CLAUDE.md's "Compilation Pipeline" diagram and "Core Compiler Phases"
 * list (and their Japanese translation in docs/ja/CLAUDE_ja.md) against the real phase
 * sequence wired up in `PipelineRunner.scala`. `MutualRecursionOptimization` runs as its
 * own phase, right after `TailCallOptimization` and before code generation — but the
 * top-level CLAUDE.md files were only ever showing a 5-step pipeline (Parsing, Rewriting,
 * Type Checking, Tail Call Optimization, Code Generation), silently skipping it, unlike
 * docs/reference/compiler-architecture.md, which already documented it correctly.
 */
class ClaudeMdPipelinePhaseParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def numberedPhasesUnder(doc: String, heading: String): Seq[String] = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == heading)
    assert(start >= 0, s"could not find heading '$heading' — the scan has rotted")
    lines.drop(start + 1)
      .takeWhile(l => !l.trim.startsWith("### ") && !l.trim.startsWith("## "))
      .filter(_.matches("^\\d+\\. \\*\\*.*"))
  }

  it("mentions MutualRecursionOptimization in CLAUDE.md's pipeline diagram and phase list") {
    val en = read("CLAUDE.md")
    assert(en.contains("Mutual Recursion Optimization"),
      "CLAUDE.md's Compilation Pipeline diagram doesn't mention Mutual Recursion Optimization, " +
      "a real phase run by PipelineRunner.scala between Tail Call Optimization and Code Generation")
    assert(en.contains("MutualRecursionOptimization.scala"),
      "CLAUDE.md's Core Compiler Phases list doesn't reference optimization/MutualRecursionOptimization.scala")
  }

  it("mentions the same phase in docs/ja/CLAUDE_ja.md") {
    val ja = read("docs/ja/CLAUDE_ja.md")
    assert(ja.contains("相互再帰最適化"),
      "docs/ja/CLAUDE_ja.md's pipeline diagram doesn't mention 相互再帰最適化 (Mutual Recursion Optimization)")
    assert(ja.contains("MutualRecursionOptimization.scala"),
      "docs/ja/CLAUDE_ja.md's phase list doesn't reference optimization/MutualRecursionOptimization.scala")
  }

  it("has the same number of numbered Core Compiler Phases entries in English and Japanese") {
    val en = numberedPhasesUnder(read("CLAUDE.md"), "### Core Compiler Phases")
    val ja = numberedPhasesUnder(read("docs/ja/CLAUDE_ja.md"), "### コアコンパイラフェーズ")
    assert(en.size == ja.size,
      s"CLAUDE.md has ${en.size} numbered phase entries under Core Compiler Phases " +
      s"but docs/ja/CLAUDE_ja.md has ${ja.size} under コアコンパイラフェーズ")
  }
}
