package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Regression for #2059: `?:` (Elvis) already proves its boxed result is
 * non-null whenever the fallback can't be null, so unboxing that result into
 * a primitive must not warn W0015 -- unlike an unrelated boxed platform value
 * of genuinely unknown nullability (PlatformUnboxingWarningSpec).
 */
class ElvisPlatformUnboxingFalsePositiveSpec extends AnyFunSpec {

  private def compileWarnings(source: String, level: WarningLevel = WarningLevel.On) = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = level)
    new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "W.on")))
  }

  describe("W0015 does not fire on an Elvis operand it has already proven non-null") {
    it("does not warn for `x ?: 0` on a nullable Int") {
      val result = compileWarnings(
        """
          |class Test {
          |public:
          |  static def g(x: Int?): Int = x ?: 0
          |  static def main(args: String[]): Int {
          |    return g(null)
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors)
      val w15 = result.diagnostics.warnings.filter(_.category.code == "W0015")
      assert(w15.isEmpty, s"expected no W0015, got: ${result.diagnostics.warnings.map(_.message)}")
    }

    it("does not warn for `s?.length ?: -1` on a nullable String") {
      val result = compileWarnings(
        """
          |class Test {
          |public:
          |  static def f(s: String?): Int = s?.length ?: -1
          |  static def main(args: String[]): Int {
          |    return f("abc")
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors)
      val w15 = result.diagnostics.warnings.filter(_.category.code == "W0015")
      assert(w15.isEmpty, s"expected no W0015, got: ${result.diagnostics.warnings.map(_.message)}")
    }
  }
}
