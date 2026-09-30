package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the CLI flags `onionc`/`onion` accept.
 *
 * `CompilerOptions.scala` is the single source of truth for every flag both front ends
 * share (`sharedOptionConfigs`). CLAUDE.md's "Compiler Options (for onionc/onion)" section
 * documents a hand-maintained subset of them, and nothing previously checked that subset
 * against the real flag registry, so a flag added to the compiler (e.g. `--profile-compile`)
 * could go undocumented indefinitely. `ClaudeMdCompilerOptionsParitySpec` only checks that
 * the English and Japanese bullet counts match each other, not that either side is complete.
 */
class CompilerOptionsDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private lazy val actualFlags: Set[String] =
    """final val \w+: String = "([^"]+)"""".r
      .findAllMatchIn(read("src/main/scala/onion/tools/CompilerOptions.scala"))
      .map(_.group(1))
      .filter(_.startsWith("-"))
      .toSet

  it("actual CompilerOptions.scala declares the flags this guard assumes (sanity check)") {
    assert(actualFlags.nonEmpty, "reflection on CompilerOptions.scala found no flags -- the scan has rotted")
    assert(actualFlags.contains("--profile-compile"), "expected --profile-compile among the declared flags")
  }

  it("CLAUDE.md mentions every onionc/onion CLI flag") {
    val doc = read("CLAUDE.md")
    val missing = actualFlags.filterNot(doc.contains)
    assert(missing.isEmpty, s"CLAUDE.md is missing CLI flags: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/CLAUDE_ja.md mentions every onionc/onion CLI flag") {
    val doc = read("docs/ja/CLAUDE_ja.md")
    val missing = actualFlags.filterNot(doc.contains)
    assert(missing.isEmpty, s"docs/ja/CLAUDE_ja.md is missing CLI flags: ${missing.toSeq.sorted.mkString(", ")}")
  }
}
