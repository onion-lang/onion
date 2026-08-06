package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard tying `docs/tools/compiler.md` and `docs/tools/script-runner.md` (and
 * their `docs/ja` translations) to the actual `--help` option lists in
 * `CompilerFrontend.printUsage` and `ScriptRunner.printUsage`. `-super` and
 * `--verbose` are real, working flags on both CLIs but had no `### \`<flag>\`` section
 * on any of the four pages; `--effects` was additionally missing from both
 * `compiler.md` pages. Nothing tied the option reference to the usage string, so a
 * flag added to one and not the other could drift silently — this is that tie.
 */
class CliOptionDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  // `-h`/`--help` and `-v`/`--version` share one usage line and aren't documented as
  // standalone `###` sections anywhere; every other flag is expected to have one.
  private val undocumented = Set("-h", "--help", "-v", "--version")

  private def optionsFrom(usage: String): Set[String] = {
    val optionsSection = usage.substring(usage.indexOf("Options:"))
    """\|\s+(-{1,2}[A-Za-z][\w-]*)""".r
      .findAllMatchIn(optionsSection)
      .map(_.group(1))
      .toSet
      .diff(undocumented)
  }

  private lazy val compilerOptions: Set[String] =
    optionsFrom(read("src/main/scala/onion/tools/CompilerFrontend.scala"))

  private lazy val scriptRunnerOptions: Set[String] =
    optionsFrom(read("src/main/scala/onion/tools/ScriptRunner.scala"))

  private def check(path: String, options: Set[String]): Unit = {
    assert(options.nonEmpty, s"no options extracted for $path — the scan has rotted")
    val doc = read(path)
    val missing = options.filterNot(opt => doc.contains(s"`$opt")).toSeq.sorted
    assert(missing.isEmpty, s"$path has no section for: ${missing.mkString(", ")}")
  }

  it("docs/tools/compiler.md documents every onionc option") {
    check("docs/tools/compiler.md", compilerOptions)
  }

  it("docs/ja/tools/compiler.md documents every onionc option") {
    check("docs/ja/tools/compiler.md", compilerOptions)
  }

  it("docs/tools/script-runner.md documents every onion option") {
    check("docs/tools/script-runner.md", scriptRunnerOptions)
  }

  it("docs/ja/tools/script-runner.md documents every onion option") {
    check("docs/ja/tools/script-runner.md", scriptRunnerOptions)
  }
}
