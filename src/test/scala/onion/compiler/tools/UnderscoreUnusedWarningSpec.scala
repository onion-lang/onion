package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * `_` is the conventional "I'm intentionally discarding this binding" name
 * (Scala, Rust, Go, and Onion's own `run/` examples all rely on it, e.g.
 * `foreach (k, _) in map { ... }` to keep only one half of a destructured
 * pair). W0001/W0006 (unused variable/parameter) must not fire for it --
 * flagging the one name whose entire point is "unused" is a false positive
 * that trains users to ignore the warning instead of acting on it.
 */
class UnderscoreUnusedWarningSpec extends AnyFunSpec {

  private def compileWarnings(source: String) = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On)
    new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "U.on")))
  }

  describe("unused-variable/parameter warnings for `_`") {
    it("does not warn for a foreach-destructured `_` component") {
      val result = compileWarnings(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val m = ["a": 1, "b": 2]
          |    var total = args.length
          |    foreach (_, v) in m { total = total + v }
          |    return total
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors, s"unexpected errors: ${result.allErrors.map(_.message)}")
      val unused = result.diagnostics.warnings.filter(w => w.category.code == "W0001" || w.category.code == "W0006")
      assert(unused.isEmpty, s"expected no unused-variable warnings, got: ${result.diagnostics.warnings.map(_.message)}")
    }

    it("does not warn for an unused `_` parameter") {
      val result = compileWarnings(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val f = (_: Int, y: Int) -> y
          |    return f(1, 2) + args.length
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors)
      val unused = result.diagnostics.warnings.filter(w => w.category.code == "W0001" || w.category.code == "W0006")
      assert(unused.isEmpty, s"expected no unused-variable warnings, got: ${result.diagnostics.warnings.map(_.message)}")
    }

    it("still warns for a genuinely unused, normally-named local variable") {
      val result = compileWarnings(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val unused = 42
          |    return 0
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors)
      val w1 = result.diagnostics.warnings.filter(_.category.code == "W0001")
      assert(w1.length == 1, s"expected 1 W0001, got: ${result.diagnostics.warnings.map(_.message)}")
    }
  }
}
