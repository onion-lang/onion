package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, ScalarConversions, StreamInputSource}
import onion.compiler.toolbox.Message
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Regression test for issue #2460: a varargs parameter (`files: String...`) on a
 * top-level auto-CLI `main` or on a `tool` was read as ONE `String`, because the
 * CLI checks looked only at the element type. `files.length` was 1 whatever the
 * user passed, and `--contract` described the array as `"type":"String"`. It is
 * now rejected like any other type the command line cannot fill: E0094 for
 * `main`, whose message points at the `String[]` rest collector, and E0081 for a
 * `tool`. Varargs elsewhere (a helper function, a class's `static def main`) are
 * unaffected.
 *
 * Expected text is resolved through `Message(...)`, so this holds in both the
 * `en` and `ja` full-suite runs.
 */
class VarargsCliParameterSpec extends AnyFunSpec {

  private def compile(code: String) =
    new OnionCompiler(CompilerConfig(Seq("."), null, "UTF-8", "", 10)).compileDetailed(
      Seq(new StreamInputSource(() => new StringReader(code), "Test.on"))
    )

  private def assertRejected(code: String, errorCode: String, expected: String, line: Int): Unit = {
    val result = compile(code)
    assert(result.hasErrors, s"expected $errorCode, but it compiled")
    val errors = result.allErrors
    val hit = errors.find(_.code.contains(errorCode))
    assert(hit.isDefined, s"expected $errorCode, got: ${errors.map(e => (e.code, e.message))}")
    assert(hit.get.message.contains(expected), s"expected '$expected', got: ${hit.get.message}")
    assert(hit.get.location.line == line, s"expected line $line, got: ${hit.get.location}")
  }

  private def mainMessage(name: String, tpe: String): String =
    Message("error.semantic.mainParameterNotCliConvertible", Array[Any](name, tpe, ScalarConversions.supportedNames))

  describe("a varargs parameter on a top-level main") {
    it("is E0094 after a scalar, naming the type as String...") {
      assertRejected(
        """
          |def main(cmd: String, files: String...): void {
          |  IO::println("cmd=" + cmd + " n=" + files.length)
          |}
          |""".stripMargin,
        "E0094", mainMessage("files", "String..."), 2)
    }

    it("is E0094 as the only parameter") {
      assertRejected(
        """
          |def main(args: String...): void {
          |  IO::println("n=" + args.length)
          |}
          |""".stripMargin,
        "E0094", mainMessage("args", "String..."), 2)
    }

    it("is E0094 for a non-String element type") {
      assertRejected(
        """
          |def main(n: Int...): void {
          |  IO::println("n=" + n.length)
          |}
          |""".stripMargin,
        // A primitive element renders in the AST's own spelling (`int`), as in every
        // E0094 today; only the code, the place and the varargs marker matter here.
        "E0094", "...", 2)
    }
  }

  describe("a varargs parameter on a tool") {
    it("is E0081, naming the type as String...") {
      assertRejected(
        """
          |tool lint(files: String...) requires { console } {
          |  IO::println("n=" + files.length)
          |}
          |""".stripMargin,
        "E0081",
        Message("error.semantic.toolParameterNotCliConvertible",
          Array[Any]("lint", "files", "String...", ScalarConversions.supportedNames)),
        2)
    }
  }

  describe("varargs outside the command line") {
    it("still compiles in a helper function next to an auto-CLI main") {
      val result = compile(
        """
          |def count(xs: String...): Int = xs.length
          |def main(cmd: String): void {
          |  IO::println(cmd + count("a", "b"))
          |}
          |""".stripMargin
      )
      assert(!result.hasErrors, s"unexpected errors: ${result.allErrors.map(_.message)}")
    }

    it("still compiles a class's static def main(args: String...)") {
      val result = compile(
        """
          |class App {
          |public:
          |  static def main(args: String...): void {
          |    IO::println("n=" + args.length)
          |  }
          |}
          |""".stripMargin
      )
      assert(!result.hasErrors, s"unexpected errors: ${result.allErrors.map(_.message)}")
    }
  }
}
