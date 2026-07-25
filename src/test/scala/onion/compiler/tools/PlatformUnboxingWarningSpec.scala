package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Tests for W0015 (issue #318): a boxed platform value (e.g. Json::getInt's
 * Integer, null when the key is missing) implicitly unboxed to a non-null
 * primitive warns, since the unboxing throws a raw NullPointerException at
 * runtime rather than surfacing the absence.
 */
class PlatformUnboxingWarningSpec extends AnyFunSpec {

  private def compileWarnings(source: String, level: WarningLevel = WarningLevel.On) = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = level)
    new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "W.on")))
  }

  describe("W0015 platform unboxing") {
    it("warns when Json::getInt (boxed Integer) is assigned to a non-null Int") {
      val result = compileWarnings(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val obj = Json::parse("{}")
          |    val n: Int = Json::getInt(obj, "missing")
          |    return n
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors)
      val w15 = result.diagnostics.warnings.filter(_.category.code == "W0015")
      assert(w15.length == 1, s"expected 1 W0015, got: ${result.diagnostics.warnings.map(_.message)}")
    }

    it("does not warn for the defaulted accessor (already primitive)") {
      val result = compileWarnings(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val obj = Json::parse("{}")
          |    val n: Int = Json::getIntOr(obj, "missing", 0)
          |    return n
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors)
      assert(result.diagnostics.warnings.filter(_.category.code == "W0015").isEmpty)
    }

    it("fails compilation under warnings-as-errors") {
      val result = compileWarnings(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val obj = Json::parse("{}")
          |    val n: Int = Json::getInt(obj, "missing")
          |    return n
          |  }
          |}
          |""".stripMargin,
        WarningLevel.Error)
      assert(result.hasErrors)
    }
  }
}
