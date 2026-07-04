package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Tests for W0013 (issue #266): a plain string literal containing shell/Kotlin
 * style interpolation (`${expr}` or `$identifier`) is emitted verbatim by Onion
 * (which interpolates with `#{expr}`), so the compiler warns instead of leaving
 * it a silent footgun. A lone `$` (e.g. a price string) must not warn, and at
 * most one warning is emitted per literal.
 */
class SuspiciousInterpolationSpec extends AnyFunSpec {

  private def compileWarnings(source: String, level: WarningLevel = WarningLevel.On) = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = level)
    new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "W.on")))
  }

  private def w13(result: onion.compiler.pipeline.CompilationResult) =
    result.diagnostics.warnings.filter(_.category.code == "W0013")

  describe("W0013 suspicious string interpolation") {
    it("warns on ${expr} form") {
      val result = compileWarnings(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a: String = "hi"
          |    return "value is ${a}"
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors)
      assert(w13(result).length == 1, s"warnings: ${result.diagnostics.warnings.map(_.message)}")
    }

    it("warns on $identifier form") {
      val result = compileWarnings(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a: String = "hi"
          |    return "var is $a"
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors)
      assert(w13(result).length == 1)
    }

    it("does NOT warn on a lone $ before a digit (price string)") {
      val result = compileWarnings(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return "the price is $5 and $10"
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors)
      assert(w13(result).isEmpty, s"unexpected: ${w13(result).map(_.message)}")
    }

    it("does NOT warn on a trailing or standalone $") {
      val result = compileWarnings(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return "cost: $" + " and $"
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors)
      assert(w13(result).isEmpty)
    }

    it("emits at most one warning per literal") {
      val result = compileWarnings(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a: String = "hi"
          |    return "two vars $a and ${a} in one literal"
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors)
      assert(w13(result).length == 1, s"warnings: ${w13(result).map(_.message)}")
    }

    it("does NOT warn on plain strings or #{} interpolation") {
      val result = compileWarnings(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a: String = "hi"
          |    return "plain string, and #{a} interpolated, and #{a} again"
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors)
      assert(w13(result).isEmpty)
    }
  }
}
