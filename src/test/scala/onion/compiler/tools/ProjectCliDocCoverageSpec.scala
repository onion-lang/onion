package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard tying `OnionCli`'s subcommand list to `docs/tools/project-cli.md` and its
 * `docs/ja` translation, in both directions.
 *
 * `onion.tools.doc.OnionDoc` was a complete HTML generator — six files, its own `-d` and
 * `--help` — that no launcher, subcommand, build task or documentation page ever named. It
 * stayed that way for months and nothing failed, because nothing was checking. A feature
 * nobody can find is indistinguishable from one that does not exist.
 *
 * The reverse direction matters just as much: a page describing a subcommand that has been
 * renamed or removed sends people to a command that errors out, which is worse than saying
 * nothing at all.
 *
 * This compares subcommand *names* rather than whole usage lines, because the Japanese page
 * translates the placeholders (`<dir>` becomes `<ディレクトリ>`) and should keep doing so.
 */
class ProjectCliDocCoverageSpec extends AnyFunSpec {

  private def read(path: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(path))

  /** Subcommands named in the `Usage` string, e.g. `new`, `build`, `fmt`. */
  private lazy val subcommands: Set[String] = {
    val source = read("src/main/scala/onion/tools/OnionCli.scala")
    val usage = source.substring(source.indexOf("Usage:"), source.indexOf("\"\"\".stripMargin"))
    """\|\s+onion (\w+)""".r.findAllMatchIn(usage).map(_.group(1)).toSet
  }

  /** Subcommands named in a page's `## Commands` code block. */
  private def documented(path: String): Set[String] = {
    val page = read(path)
    val heading = """(?m)^## (Commands|コマンド)$""".r.findFirstMatchIn(page)
      .getOrElse(fail(s"$path has no Commands section — the scan has rotted"))
    val fenceStart = page.indexOf("```text", heading.end)
    assert(fenceStart >= 0, s"$path has no command listing after its Commands heading")
    val fenceEnd = page.indexOf("```", fenceStart + 7)
    val block = page.substring(fenceStart, fenceEnd)
    """(?m)^onion (\w+)""".r.findAllMatchIn(block).map(_.group(1)).toSet
  }

  private def check(path: String): Unit = {
    assert(subcommands.nonEmpty, "no subcommands extracted from OnionCli — the scan has rotted")
    val listed = documented(path)
    // `repl` shares its usage line with the script runner and is documented on its own page.
    val expected = subcommands - "repl"
    val missing = (expected -- listed).toSeq.sorted
    val stale = (listed -- subcommands).toSeq.sorted
    assert(missing.isEmpty, s"$path does not list: ${missing.mkString(", ")}")
    assert(stale.isEmpty, s"$path lists commands OnionCli does not have: ${stale.mkString(", ")}")
  }

  it("docs/tools/project-cli.md lists exactly the subcommands OnionCli accepts") {
    check("docs/tools/project-cli.md")
  }

  it("docs/ja/tools/project-cli.md lists exactly the subcommands OnionCli accepts") {
    check("docs/ja/tools/project-cli.md")
  }

  it("every listed subcommand has a section explaining it on both pages") {
    // The listing alone is a synopsis; without prose, a reader learns the command exists and
    // nothing about what it does.
    val explained = Set("test", "doc", "fmt")
    Seq("docs/tools/project-cli.md", "docs/ja/tools/project-cli.md").foreach { path =>
      val page = read(path)
      val missing = explained.filterNot(name => page.contains(s"### `onion $name")).toSeq.sorted
      assert(missing.isEmpty, s"$path has no section for: ${missing.mkString(", ")}")
    }
  }
}
