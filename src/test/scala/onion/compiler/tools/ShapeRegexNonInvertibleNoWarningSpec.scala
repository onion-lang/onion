package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * `shape name = re"..."` on a non-invertible pattern synthesizes a read-only shape by
 * passing a literal `null` printer to `onion.Shapes::regex` (Rewriting.synthesizeRegexShape).
 * That `null` is the compiler's own, not anything the user wrote, so it must not trip the
 * W0012 null-to-non-nullable warning (NullToNonNullableSpec, NullToTypeVariableGenericSpec)
 * onto the user's `shape` clause -- which it did, since the printer parameter's type is
 * `Function1<T, String>` with `T` resolved to the record, a non-nullable generic reference
 * type exactly like the ones those specs correctly warn on for user-written code.
 */
class ShapeRegexNonInvertibleNoWarningSpec extends AnyFunSpec {

  private def compileWarnings(source: String) = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On)
    new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "W.on")))
  }

  describe("a non-invertible shape ... = re\"...\" pattern") {
    it("synthesizes its read-only (null-printer) shape with no W0012 warning") {
      val result = compileWarnings(
        """
          |record Pt(x: Int, y: Int) {
          |  shape loose = re"(-?\d+)\s+(-?\d+)"
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "ok" }
          |}
          |""".stripMargin)
      assert(!result.hasErrors)
      val w12 = result.diagnostics.warnings.filter(_.category.code == "W0012")
      assert(w12.isEmpty, s"expected no W0012, got: ${result.diagnostics.warnings.map(_.message)}")
    }
  }
}
