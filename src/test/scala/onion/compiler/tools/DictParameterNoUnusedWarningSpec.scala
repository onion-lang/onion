package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * A `[T: Trait]` function gets a synthesized dictionary parameter (`dict$Trait$T`). The
 * user never wrote it and cannot rename or remove it, so W0006 must not name it, even when
 * the body only forwards the constraint (#2792).
 */
class DictParameterNoUnusedWarningSpec extends AnyFunSpec {

  private def compile(source: String) = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On)
    new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "W.on")))
  }

  private val prelude =
    """
      |trait Numeric[T] {
      |  def zero(): T
      |  def plus(a: T, b: T): T
      |}
      |instance Numeric[Integer] {
      |  def zero(): Integer = 0
      |  def plus(a: Integer, b: Integer): Integer = a + b
      |}
      |def sum[T: Numeric](xs: List[T]): T {
      |  var acc: T = Numeric[T]::zero()
      |  foreach x in xs { acc = Numeric[T]::plus(acc, x) }
      |  return acc
      |}
      |""".stripMargin

  private def w0006(source: String) = {
    val result = compile(source)
    assert(!result.hasErrors, result.diagnostics.warnings.map(_.message))
    result.diagnostics.warnings.filter(_.category.code == "W0006").map(_.message)
  }

  describe("a [T: Trait] function") {
    it("that only forwards its instance has no W0006") {
      val w = w0006(prelude + "def total[T: Numeric](xs: List[T]): T = sum(xs)\nprintln(total([1, 2, 4]))\n")
      assert(w.isEmpty, s"unexpected: $w")
    }
    it("that never uses its instance has no W0006 naming dict$") {
      val w = w0006(prelude + "def first[T: Numeric](xs: List[T]): T = xs[0]\nprintln(first([1]))\n")
      assert(w.forall(!_.contains("dict$")), s"unexpected: $w")
    }
    it("still reports a user-written unused parameter") {
      val w = w0006(prelude + "def g[T: Numeric](xs: List[T], unusedOne: Int): T = sum(xs)\nprintln(g([1], 2))\n")
      assert(w.exists(_.contains("unusedOne")), s"expected unusedOne, got: $w")
    }
  }
}
