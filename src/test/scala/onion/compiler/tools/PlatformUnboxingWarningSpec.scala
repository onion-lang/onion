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

    it("does not warn when the boxed value is the left operand of ?: with a non-null fallback") {
      val result = compileWarnings(
        """
          |def g(x: Int?): Int = x ?: 0
          |def f(s: String?): Int = s?.length ?: -1
          |println(g(null))
          |println(f("abc"))
          |""".stripMargin)
      assert(!result.hasErrors)
      val w15 = result.diagnostics.warnings.filter(_.category.code == "W0015")
      assert(w15.isEmpty, s"unexpected W0015: ${w15.map(_.message)}")
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

    it("warns for List::fold's accumulator even when every argument is a non-null Int") {
      // onion.Colls::fold<T, R> is a Java generic method: its declared return
      // type is the type variable R, a boxed reference with no Onion-tracked
      // nullability, regardless of what the call site's own arguments pin R
      // to. So the single most idiomatic numeric fold -- a literal `0` zero
      // and an Int-returning combiner -- still unboxes through this warning
      // (see docs/reference/stdlib.md's Colls Module section).
      val result = compileWarnings(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val xs: List[Int] = [1, 2, 3]
          |    val total: Int = xs.fold(0) { a, b -> a + b }
          |    return total
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors)
      val w15 = result.diagnostics.warnings.filter(_.category.code == "W0015")
      assert(w15.length == 1, s"expected 1 W0015, got: ${result.diagnostics.warnings.map(_.message)}")
    }

    it("warns for List::reduce's 3-arg accumulator the same way, despite its concretely-typed signature") {
      val result = compileWarnings(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val xs: List[Int] = [1, 2, 3]
          |    val total: Int = xs.reduce(0, (acc, x) -> acc + x)
          |    return total
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors)
      val w15 = result.diagnostics.warnings.filter(_.category.code == "W0015")
      assert(w15.length == 1, s"expected 1 W0015, got: ${result.diagnostics.warnings.map(_.message)}")
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
