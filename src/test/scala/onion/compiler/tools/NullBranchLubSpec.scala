package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource, WarningLevel}
import onion.compiler.pipeline.CompilationResult
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * When an if/select expression mixes a `null` branch with a reference-typed
 * branch, the least-upper-bound of the branches must be the *nullable* form of
 * that reference type (e.g. `String?`), not the raw type. Otherwise W0012 fires
 * spuriously when the merged value flows into an already-nullable target such as
 * a `String?` return. Real null-to-non-nullable cases must still warn.
 */
class NullBranchLubSpec extends AnyFunSpec {

  private def compileWarnings(source: String, level: WarningLevel = WarningLevel.On) = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = level)
    new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "W.on")))
  }

  private def w12(result: CompilationResult) =
    result.diagnostics.warnings.filter(_.category.code == "W0012")

  describe("null-branch LUB does not trip W0012 against a nullable target") {
    it("does not warn for a select with a null branch returned as String?") {
      val result = compileWarnings(
        """
          |class Test {
          |public:
          |  static def lookup(k: Int): String? {
          |    return select k {
          |      case 1: "one"
          |      case 2: "two"
          |      else: null
          |    }
          |  }
          |  static def main(args: String[]): String {
          |    return "" + lookup(1) + lookup(9)
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors)
      assert(w12(result).isEmpty, s"unexpected W0012: ${result.diagnostics.warnings.map(_.message)}")
    }

    it("does not warn for an if/else with a null branch returned as String?") {
      val result = compileWarnings(
        """
          |class Test {
          |public:
          |  static def pick(b: Boolean): String? {
          |    return if b { "yes" } else { null }
          |  }
          |  static def main(args: String[]): String {
          |    return "" + pick(true) + pick(false)
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors)
      assert(w12(result).isEmpty, s"unexpected W0012: ${result.diagnostics.warnings.map(_.message)}")
    }

    it("emits no W0012 under warnings-as-errors for the nullable-merge case") {
      val result = compileWarnings(
        """
          |class Test {
          |public:
          |  static def pick(b: Boolean): String? {
          |    return if b { "yes" } else { null }
          |  }
          |  static def main(args: String[]): String {
          |    return "" + pick(args.length > 0)
          |  }
          |}
          |""".stripMargin,
        WarningLevel.Error)
      // The nullable branch merge must not contribute a W0012 (which under
      // warnings-as-errors would surface as an error).
      assert(w12(result).isEmpty, s"unexpected W0012: ${result.diagnostics.warnings.map(_.message)}")
    }

    it("still warns when null flows into a genuinely non-nullable target") {
      val result = compileWarnings(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val s: String = null
          |    return s
          |  }
          |}
          |""".stripMargin)
      assert(w12(result).nonEmpty, "expected W0012 for direct null-to-non-nullable")
    }
  }
}
