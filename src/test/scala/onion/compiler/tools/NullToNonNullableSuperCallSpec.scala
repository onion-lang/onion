package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Issue #2477: `null` passed to a non-null parameter in a superclass init
 * (`extends P(null)`) or a delegating `: this(...)` call gets W0012, as
 * `new P(null)` does. A nullable parameter or a Java constructor stays silent.
 */
class NullToNonNullableSuperCallSpec extends AnyFunSpec {

  private def w12Lines(source: String): Seq[Int] = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On)
    val result = new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "W.on")))
    assert(!result.hasErrors, s"unexpected errors: ${result.diagnostics}")
    result.diagnostics.warnings.filter(_.category.code == "W0012").map(_.location.line).toSeq
  }

  describe("W0012 on super and self constructor calls") {
    it("warns for extends P(null) and : this(a, null)") {
      val lines = w12Lines(
        """class P(val n: String)
          |class C extends P(null) { }
          |class D(val a: Int, val b: String) {
          |public:
          |  def this(a: Int) : this(a, null) { }
          |}
          |""".stripMargin)
      assert(lines == Seq(2, 5), s"W0012 lines: $lines")
    }

    it("stays silent for a nullable parameter and a Java superclass") {
      val lines = w12Lines(
        """class P(val n: String?)
          |class C extends P(null) { }
          |class T extends java.lang.Thread(null as java.lang.Runnable) { }
          |""".stripMargin)
      assert(lines.isEmpty, s"W0012 lines: $lines")
    }

    it("substitutes a generic parent's type arguments before deciding") {
      val lines = w12Lines(
        """class G[T](val x: T)
          |class H extends G[String?](null) { }
          |class W[U] extends G[U](null) { }
          |class S extends G[String](null) { }
          |""".stripMargin)
      assert(lines == Seq(4), s"W0012 lines: $lines")
    }

    it("stays silent for : this(null) into a bare type parameter, warns for a non-null bound") {
      val lines = w12Lines(
        """class D[T](val a: T) {
          |public:
          |  def this() : this(null) { }
          |}
          |class B[T extends Object](val a: T) {
          |public:
          |  def this() : this(null) { }
          |}
          |""".stripMargin)
      assert(lines == Seq(7), s"W0012 lines: $lines")
    }
  }
}
