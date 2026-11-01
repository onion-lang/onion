package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Issue #2500: a bare `[T]` admits nullable type arguments, so `null` is a valid
 * `T` and passing or assigning it does not warn W0012. `[T extends Object]` is
 * non-null and still warns, and so does a receiver that pins `T` to a non-null
 * type (`D[String]`, `D[Object]`).
 */
class NullToBareTypeVariableSpec extends AnyFunSpec {

  private def w12Lines(source: String): Seq[Int] = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On)
    val result = new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "W.on")))
    assert(!result.hasErrors, s"unexpected errors: ${result.diagnostics}")
    result.diagnostics.warnings.filter(_.category.code == "W0012").map(_.location.line).toSeq
  }

  describe("null where a bare T is expected") {
    it("does not warn inside the generic class (the issue's repro)") {
      val lines = w12Lines(
        """class D[T](val a: T) {
          |public:
          |  def m(x: T): void { }
          |  def k(): void { m(null); val d = new D[T](null) }
          |  def j(): void { this.m(null); val y: T = null }
          |}
          |println("ok")
          |""".stripMargin)
      assert(lines.isEmpty, s"W0012 lines: $lines")
    }

    it("still warns for [T extends Object]") {
      val lines = w12Lines(
        """class E[T extends Object](val a: T) {
          |public:
          |  def m(x: T): void { }
          |  def k(): void {
          |    m(null)
          |    val e = new E[T](null)
          |    val y: T = null
          |  }
          |}
          |println("ok")
          |""".stripMargin)
      assert(lines == Seq(5, 6, 7), s"W0012 lines: $lines")
    }

    it("still warns when the receiver pins T to a non-null type") {
      val lines = w12Lines(
        """class D[T](val a: T) {
          |public:
          |  def m(x: T): void { }
          |}
          |new D[String]("a").m(null)
          |new D[Object]("a").m(null)
          |new D[String?]("a").m(null)
          |val d = new D[String](null)
          |""".stripMargin)
      assert(lines == Seq(5, 6, 8), s"W0012 lines: $lines")
    }
  }
}
