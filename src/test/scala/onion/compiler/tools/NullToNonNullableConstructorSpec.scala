package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Issue #2464: `null` passed to a non-null parameter of an Onion-declared
 * constructor gets W0012, as the same `null` passed to a method does. A Java
 * constructor stays silent (its parameter nullness is unknown, #2116).
 */
class NullToNonNullableConstructorSpec extends AnyFunSpec {

  private def w12Lines(source: String): Seq[Int] = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On)
    val result = new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "W.on")))
    assert(!result.hasErrors, s"unexpected errors: ${result.diagnostics}")
    result.diagnostics.warnings.filter(_.category.code == "W0012").map(_.location.line).toSeq
  }

  describe("W0012 on constructor arguments") {
    it("warns for a primary constructor, a record and an explicit constructor") {
      val lines = w12Lines(
        """class Q(val n: String) {
          |public:
          |  def m(s: String): Int = 0
          |}
          |record R(s: String)
          |class E {
          |public:
          |  def this(s: String, k: Int) { }
          |}
          |new Q("a").m(null)
          |val q = new Q(null)
          |val r = new R(null)
          |val e = new E(null, 1)
          |""".stripMargin)
      assert(lines == Seq(10, 11, 12, 13), s"W0012 lines: $lines")
    }

    it("warns for a named argument and next to a defaulted parameter") {
      val lines = w12Lines(
        """class D(val a: String, val b: String = "x")
          |val d1 = new D(null)
          |val d2 = new D(a = null)
          |val d3 = new D("a", b = null)
          |""".stripMargin)
      assert(lines == Seq(2, 3, 4), s"W0012 lines: $lines")
    }

    it("warns for an applied generic class whose type argument is non-null") {
      val lines = w12Lines(
        """class Box[T](val v: T, val label: String)
          |val b = new Box[String?](null, null)
          |""".stripMargin)
      assert(lines == Seq(2), s"W0012 lines: $lines")
    }

    it("does not warn for a nullable parameter or a Java constructor") {
      val lines = w12Lines(
        """class N(val n: String?)
          |record M(s: String?)
          |val n = new N(null)
          |val m = new M(null)
          |val j = new java.util.StringJoiner(null)
          |""".stripMargin)
      assert(lines.isEmpty, s"W0012 lines: $lines")
    }
  }
}
