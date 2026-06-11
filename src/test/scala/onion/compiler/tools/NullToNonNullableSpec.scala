package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Tests for W0012 (issue #132 option B): the null literal flowing into a
 * non-nullable reference type warns (and fails under --warn error), while
 * nullable targets stay silent. Values from Java remain unchecked
 * (platform-type dilemma).
 */
class NullToNonNullableSpec extends AnyFunSpec {

  private def compileWarnings(source: String, level: WarningLevel = WarningLevel.On) = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = level)
    new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "W.on")))
  }

  describe("W0012 null to non-nullable") {
    it("warns on declaration and assignment, but not for nullable targets") {
      val result = compileWarnings(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val s: String = null
          |    val ok: String? = null
          |    var t: String = "x"
          |    t = null
          |    return "" + s + ok + t
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors)
      val w12 = result.diagnostics.warnings.filter(_.category.code == "W0012")
      assert(w12.length == 2, s"expected 2 W0012, got: ${result.diagnostics.warnings.map(_.message)}")
    }

    it("fails compilation under warnings-as-errors") {
      val result = compileWarnings(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val s: String = null
          |    return s
          |  }
          |}
          |""".stripMargin,
        WarningLevel.Error)
      assert(result.hasErrors)
    }

    it("warns when null is passed as a non-nullable argument") {
      val result = compileWarnings(
        """
          |class Test {
          |public:
          |  static def shout(s: String): String { return s + "!" }
          |  static def main(args: String[]): String {
          |    return shout(null)
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors)
      assert(result.diagnostics.warnings.exists(_.category.code == "W0012"))
    }
  }
}
