package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilerConfig, ContractMode, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

/**
 * A `history` sees every compound assignment, not only `+=` and `-=` (#2603). Under
 * `history n >= old(n)`, `n *= -1` takes 10 to -10: running it reproduces the solver's
 * counterexample (E0112). `/=`, `%=` and the bitwise forms are not modelled by the solver, so each
 * is the warning W0024 rather than silence.
 */
class HistoryCompoundWriteSpec extends AnyFunSpec {
  private final case class Outcome(errors: Seq[String], warnings: Seq[String], w24: Seq[String])

  private def compile(src: String): Outcome = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On, contracts = ContractMode.Check)
    val r = new OnionCompiler(config).compileDetailed(Seq(new StreamInputSource(() => new StringReader(src), "H.on")))
    Outcome(r.diagnostics.errors.flatMap(_.errorCode),
      r.diagnostics.warnings.map(_.category.code),
      r.diagnostics.warnings.filter(_.category.code == "W0024").map(_.message))
  }

  private def counter(method: String) =
    s"""class Counter {
       |  history n >= old(n)
       |public:
       |  var n: Int
       |  def this { n = 10 }
       |  $method
       |}""".stripMargin

  it("reports `n *= -1`, which takes 10 to -10, as E0112") {
    val o = compile(counter("def neg(): void { n *= -1 }"))
    assert(o.errors.contains("E0112"), o.toString)
  }

  it("proves `n *= 1`, which keeps every value") {
    val o = compile(counter("def same(): void { n *= 1 }"))
    assert(o.errors.isEmpty, o.toString)
    assert(!o.warnings.exists(c => c == "W0021" || c == "W0024"), o.toString)
  }

  for ((op, rhs) <- Seq("/=" -> "2", "%=" -> "3", "&=" -> "7", "|=" -> "1", "^=" -> "1", "<<=" -> "1", ">>=" -> "1", ">>>=" -> "1")) {
    it(s"warns W0024 for `n $op $rhs`, which the solver does not model") {
      val o = compile(counter(s"def f(): void { n $op $rhs }"))
      assert(o.errors.isEmpty, o.toString)
      assert(o.w24.exists(m => m.contains("f") && m.contains("n >= old(n)")), o.toString)
    }
  }

  it("is E0109 for `n *= 2` under `history n == old(n)`, like any other write") {
    val src =
      """class Fixed {
        |  history n == old(n)
        |public:
        |  var n: Int
        |  def this { n = 1 }
        |  def dbl(): void { n *= 2 }
        |}""".stripMargin
    val o = compile(src)
    assert(o.errors.contains("E0109"), o.toString)
  }

  it("does not count a local named like the field") {
    val o = compile(counter("def f(): void { var n: Int = 4; n /= 2 }"))
    assert(o.errors.isEmpty, o.toString)
    assert(o.w24.isEmpty, o.toString)
  }
}
