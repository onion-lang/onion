package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/** W0019 (issue #1941): discarding the result of an immutable stdlib builder step does nothing. */
class DiscardedBuilderResultWarningSpec extends AnyFunSpec {

  private def compile(body: String, level: WarningLevel = WarningLevel.On) = {
    val source =
      s"""
         |class Test {
         |public:
         |  static def main(args: String[]): void {
         |$body
         |  }
         |}
         |""".stripMargin
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = level)
    new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "W.on")))
  }

  private def w19(result: onion.compiler.pipeline.CompilationResult) =
    result.diagnostics.warnings.filter(_.category.code == "W0019")

  describe("W0019 discarded builder result") {
    it("warns when a header() step is dropped as a statement") {
      val result = compile(
        """    val req = Http::request("GET", "http://localhost/")
          |    req.header("A", "b")
          |""".stripMargin)
      assert(!result.hasErrors, s"unexpected errors: ${result.allErrors.map(_.message)}")
      assert(w19(result).length == 1, s"got: ${result.diagnostics.warnings.map(_.message)}")
    }

    it("warns when the dropped step is the only statement of an if branch") {
      val result = compile(
        """    val req = Http::request("GET", "http://localhost/")
          |    if args.length > 0 { req.header("A", "b") }
          |""".stripMargin)
      assert(!result.hasErrors, s"unexpected errors: ${result.allErrors.map(_.message)}")
      assert(w19(result).length == 1, s"got: ${result.diagnostics.warnings.map(_.message)}")
    }

    it("does not warn when the result is bound or chained") {
      val result = compile(
        """    val req = Http::request("GET", "http://localhost/")
          |    val r2 = req.header("A", "b")
          |    val r3 = req.header("A", "b").header("C", "d")
          |    IO::println(r2.toString() + r3.toString())
          |""".stripMargin)
      assert(!result.hasErrors, s"unexpected errors: ${result.allErrors.map(_.message)}")
      assert(w19(result).isEmpty, s"got: ${result.diagnostics.warnings.map(_.message)}")
    }

    it("does not warn for a mutable Java builder such as StringBuilder") {
      val result = compile(
        """    val sb = new StringBuilder()
          |    sb.append("x")
          |    IO::println(sb.toString())
          |""".stripMargin)
      assert(!result.hasErrors, s"unexpected errors: ${result.allErrors.map(_.message)}")
      assert(w19(result).isEmpty, s"got: ${result.diagnostics.warnings.map(_.message)}")
    }

    it("does not warn for Future::onSuccess, which mutates and returns this") {
      val result = compile(
        """    val f: Future[String] = Future::async { "x" }
          |    f.onSuccess((s) -> IO::println(s))
          |""".stripMargin)
      assert(!result.hasErrors, s"unexpected errors: ${result.allErrors.map(_.message)}")
      assert(w19(result).isEmpty, s"got: ${result.diagnostics.warnings.map(_.message)}")
    }

    it("fails compilation under warnings-as-errors") {
      val result = compile(
        """    val req = Http::request("GET", "http://localhost/")
          |    req.header("A", "b")
          |""".stripMargin, WarningLevel.Error)
      assert(result.hasErrors)
    }
  }
}
