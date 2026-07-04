package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Tests for W0014 (issue #278): bare executable top-level statements are silently
 * dropped when the script also defines a `main` (which becomes the entry point),
 * so the compiler warns. Top-level `val`/`var` field initializers are NOT bare
 * statements — they run before `main` (issue #270) — and must not warn, and a
 * script with no `main` runs its top-level statements and must not warn.
 */
class DiscardedTopLevelStmtsSpec extends AnyFunSpec {

  private def compile(source: String) = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10)
    new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "W.on")))
  }

  private def w14(result: onion.compiler.pipeline.CompilationResult) =
    result.diagnostics.warnings.filter(_.category.code == "W0014")

  describe("W0014 discarded top-level statements") {
    it("warns when bare top-level statements coexist with a main") {
      val result = compile(
        """
          |IO::println("bare one")
          |IO::println("bare two")
          |def main(args: String[]): void { IO::println("main") }
          |""".stripMargin)
      assert(!result.hasErrors)
      assert(w14(result).length == 1, s"warnings: ${result.diagnostics.warnings.map(_.message)}")
    }

    it("does NOT warn for a top-level val/var field initializer with a main") {
      val result = compile(
        """
          |val greeting: String = "hi"
          |def main(args: String[]): void { IO::println(greeting) }
          |""".stripMargin)
      assert(!result.hasErrors)
      assert(w14(result).isEmpty, s"unexpected: ${w14(result).map(_.message)}")
    }

    it("does NOT warn when there is no main (top-level statements run)") {
      val result = compile(
        """
          |IO::println("this runs")
          |""".stripMargin)
      assert(!result.hasErrors)
      assert(w14(result).isEmpty)
    }
  }
}
