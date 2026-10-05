package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import onion.tools.Shell
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * CLAUDE.md and docs/reference/stdlib.md (plus their ja mirrors) once showed a bare
 * `List` annotation on the result of `Access::parseAll`/`IO::readLines` as a correct,
 * working example -- but `List` is a raw generic type, and Onion forbids raw generic
 * types in a declared position (E0066, see RawGenericAssignabilitySpec). Both doc
 * examples never actually compiled; nothing caught it because DocExamplesCompileSpec
 * only checks fully labeled `**Xxx.on**` programs under docs/examples/, not CLAUDE.md's
 * or stdlib.md's inline fragments. The docs now use `List[Access]`/`List[String]`; this
 * pins that fixed form so it cannot silently drift back to the raw one.
 */
class ClaudeMdListAnnotationExamplesCompileSpec extends AbstractShellSpec {

  private def config: CompilerConfig =
    CompilerConfig(Seq("."), null, "UTF-8", "", 100)

  private def compileErrors(code: String): Seq[String] =
    new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(code), "Doc.on")))
      .allErrors
      .map(_.message)
      .toSeq

  describe("CLAUDE.md's `from re\"...\"` + parseAll example") {
    it("runs with a List[Access] annotation, as CLAUDE.md now shows") {
      val result = shell.run(
        """
          |record Access(time: String, method: String, path: String, status: Int)
          |  from re"(\S+) (\w+) (\S+) (\d+)"
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val rows: List[Access] = Access::parseAll("127.0.0.1 GET /index 200")
          |    return rows.size
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(1) == result)
    }
  }

  describe("docs/reference/stdlib.md's IO::readLines example") {
    it("compiles with a List[String] annotation, as the doc now shows") {
      val errs = compileErrors(
        """
          |def main: void {
          |  val lines: List[String] = IO::readLines()
          |  IO::println(lines.size)
          |}
          |""".stripMargin
      )
      assert(errs.isEmpty, s"did not compile: ${errs.mkString("; ")}")
    }
  }
}
