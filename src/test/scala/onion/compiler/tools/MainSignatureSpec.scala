package onion.compiler.tools

import onion.tools.Shell
import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A top-level `main` whose parameter list fits none of the supported shapes —
 * a single `String[]` (argv), all scalars (auto-CLI), or a scalar prefix plus a
 * trailing `String[]` rest collector — used to compile to a SILENT no-op (the
 * body landed on an unreachable overload). It must now be a clean error.
 */
class MainSignatureSpec extends AbstractShellSpec {
  it("rejects main with String[] first and an extra parameter (was a silent no-op)") {
    val result = shell.run(
      """
        | def main(args: String[], flag: Boolean = false): void {
        |   IO::println("BODY RAN")
        | }
      """.stripMargin,
      "None",
      Array("x")
    )
    assert(Shell.Failure(-1) == result)
  }

  it("still accepts the conventional single String[] main") {
    val result = shell.run(
      """
        | def main(args: String[]): String {
        |   return "ok"
        | }
      """.stripMargin,
      "None",
      Array()
    )
    assert(Shell.Success("ok") == result)
  }

  it("names a List[String] parameter and points at it, suggesting String[]") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src = "def main(name: String, tags: List[String] = []): Int {\n  return 0\n}\n"
    val errors = new OnionCompiler(config).compile(
      Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(es) => es
      case _ => Seq.empty
    }
    assert(errors.size == 1)
    assert(errors.head.message.contains("'tags'"))
    assert(errors.head.message.contains("use String[]"))
  }
}
