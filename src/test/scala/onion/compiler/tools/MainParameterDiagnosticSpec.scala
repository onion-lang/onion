package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import onion.compiler.toolbox.Message
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Regression test for issue #1998: a top-level `main` parameter whose type
 * auto-CLI cannot parse (e.g. `List[String]`, reached for by anyone wanting a
 * repeated `--tag a --tag b` option) used to be rejected with a message that
 * only talked about `String[]`, carried no error code, and pointed at the
 * `def` itself rather than the offending parameter. It must now name the
 * parameter, point at it, and carry an error code (E0094), mirroring the
 * `tool` parameter diagnostic (E0081, `TOOL_PARAMETER_NOT_CLI_CONVERTIBLE`).
 *
 * Expected text is resolved via `Message(...)`, the same lookup production
 * code uses, so this holds under both the `en` and `ja` full-suite runs
 * instead of hardcoding English text that only matches one locale.
 */
class MainParameterDiagnosticSpec extends AnyFunSpec {

  private def newConfig: CompilerConfig =
    CompilerConfig(Seq("."), null, "UTF-8", "", 10)

  private def compile(code: String) =
    new OnionCompiler(newConfig).compileDetailed(
      Seq(new StreamInputSource(() => new StringReader(code), "Test.on"))
    )

  describe("a main parameter with a type auto-CLI cannot parse") {
    it("names the parameter, points at it, and carries E0094") {
      val result = compile(
        """
          |def main(name: String, count: Int = 3, tags: List[String] = []): Int {
          |  return 0
          |}
          |""".stripMargin
      )
      assert(result.hasErrors)
      val errors = result.allErrors
      assert(errors.exists(_.code.contains("E0094")), s"expected E0094, got: ${errors.map(_.code)}")

      val expected = Message(
        "error.semantic.mainParameterNotCliConvertible",
        Array[Any]("tags", "List[String]", onion.compiler.ScalarConversions.supportedNames)
      )
      assert(
        errors.exists(_.message.contains(expected)),
        s"expected a message containing '$expected', got: ${errors.map(_.message)}"
      )

      val badParam = errors.find(_.code.contains("E0094")).get
      assert(badParam.location.line == 2, s"expected the error at the 'tags' parameter's line, got: ${badParam.location}")
    }
  }

  describe("a main with a misplaced String[] parameter") {
    it("names the parameter, points at it, and carries E0095") {
      val result = compile(
        """
          |def main(args: String[], flag: Boolean = false): void {
          |  IO::println("BODY RAN")
          |}
          |""".stripMargin
      )
      assert(result.hasErrors)
      val errors = result.allErrors
      assert(errors.exists(_.code.contains("E0095")), s"expected E0095, got: ${errors.map(_.code)}")

      val expected = Message("error.semantic.mainParameterMisplacedArray", Array[Any]("args"))
      assert(
        errors.exists(_.message.contains(expected)),
        s"expected a message containing '$expected', got: ${errors.map(_.message)}"
      )
    }
  }

  describe("a valid main parameter list") {
    it("still compiles the conventional single String[] main") {
      val result = compile(
        """
          |def main(args: String[]): String {
          |  return "ok"
          |}
          |""".stripMargin
      )
      assert(!result.hasErrors, s"unexpected errors: ${result.allErrors.map(_.message)}")
    }

    it("still compiles a scalar-prefix + String[] rest collector main") {
      val result = compile(
        """
          |def main(cmd: String, files: String[]): void {
          |}
          |""".stripMargin
      )
      assert(!result.hasErrors, s"unexpected errors: ${result.allErrors.map(_.message)}")
    }
  }
}
