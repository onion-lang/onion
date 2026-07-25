package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * Two top-level `def` functions with the same name and parameter types must report
 * E0012 (duplicated function definition), not the unrelated E0011 (duplicated global
 * variable) message. `SemanticErrorReporter` mapped `SemanticError.DUPLICATE_FUNCTION`
 * to the `duplicatedGlobalVariable` message key instead of the dedicated
 * `duplicatedFunction` key, which also drops the parameter types since
 * `duplicatedGlobalVariable` only takes one placeholder.
 */
class DuplicateFunctionSpec extends AbstractShellSpec {
  private def errors(src: String): Seq[(Option[String], String)] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(e => (e.errorCode, e.message))
      case _ => Seq.empty
    }
  }

  describe("duplicate top-level function definitions") {
    it("reports E0012 with the function's name and parameter types") {
      val results = errors(
        """
          |def foo(x: Int): Int { return x }
          |def foo(x: Int): Int { return x + 1 }
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "ok" }
          |}
          |""".stripMargin
      )
      val codes = results.map(_._1)
      assert(codes.contains(Some("E0012")))
      val message = results.collectFirst { case (Some("E0012"), msg) => msg }.get
      assert(message.contains("foo"))
      assert(message.contains("Int"))
    }

    it("still allows two functions with the same name but different parameter types") {
      val results = errors(
        """
          |def bar(x: Int): Int { return x }
          |def bar(x: String): String { return x }
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "ok" }
          |}
          |""".stripMargin
      )
      assert(!results.map(_._1).contains(Some("E0012")))
    }
  }
}
