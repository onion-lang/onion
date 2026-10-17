package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Tests for issue #2116: W0012 (null to non-nullable) should not fire when the
 * callee is a Java-declared method, whose parameter nullness is unknown to the
 * compiler (same "platform type" leniency Java type variables already get),
 * not an Onion-declared method whose signature the user actually owns.
 */
class NullToNonNullableJavaCalleeSpec extends AnyFunSpec {

  private def compileWarnings(source: String) = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On)
    new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "W.on")))
  }

  describe("W0012 and Java-declared callees") {
    it("does not warn when null is passed to a Java static method's Object/varargs parameter") {
      val result = compileWarnings(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return String::format("%s", null)
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors)
      val w12 = result.diagnostics.warnings.filter(_.category.code == "W0012")
      assert(w12.isEmpty, s"expected no W0012, got: ${result.diagnostics.warnings.map(_.message)}")
    }

    it("does not warn when null is passed into a Java static generic vararg method") {
      val result = compileWarnings(
        """
          |import { java.util.Arrays }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = Arrays::asList(1, null)
          |    return "" + xs.size()
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors)
      val w12 = result.diagnostics.warnings.filter(_.category.code == "W0012")
      assert(w12.isEmpty, s"expected no W0012, got: ${result.diagnostics.warnings.map(_.message)}")
    }

    it("still warns when null is passed to an Onion-declared method's non-nullable parameter") {
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
