package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilerConfig, ContractMode, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

/**
 * A history counterexample from the solver (RFC #2557, design 7.3 and 7.4): E0112 when running
 * the compiled code reproduces it, the warning W0021 when it cannot be reproduced.
 */
class HistoryNotProvedWarningSpec extends AnyFunSpec {
  private final case class Outcome(errors: Seq[String], w21: Seq[String])

  private def compile(src: String): Outcome = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On, contracts = ContractMode.Check)
    val r = new OnionCompiler(config).compileDetailed(Seq(new StreamInputSource(() => new StringReader(src), "W.on")))
    Outcome(r.diagnostics.errors.flatMap(_.errorCode),
      r.diagnostics.warnings.filter(_.category.code == "W0021").map(_.message))
  }

  private def counter(extra: String, ctor: String = "def this { n = 0 }") =
    s"""class Counter {
       |  history n >= old(n)
       |$extra
       |public:
       |  var n: Int
       |  $ctor
       |  def inc(): void { n = n + 1 }
       |}""".stripMargin

  it("is E0112 when running inc from n = 2147483647 really breaks the history") {
    val o = compile(counter(""))
    assert(o.errors.contains("E0112"), o.toString)
  }

  it("is silent once an invariant keeps n below the maximum") {
    val o = compile(counter("  invariant n >= 0 && n < 1000000"))
    assert(o.errors.isEmpty && o.w21.isEmpty, o.toString)
  }

  it("is not E0112 when the class cannot be built to run it (no no-argument constructor)") {
    val src =
      """class Counter(start: Int) {
        |  history n >= old(n)
        |public:
        |  var n: Int
        |  def inc(): void { n = n + 1 }
        |}""".stripMargin
    val o = compile(src)
    assert(!o.errors.contains("E0112"), o.toString)
  }

  it("fails the build under --warn error when the warning is W0021 (#2595)") {
    val src =
      """class Counter {
        |  history n >= old(n)
        |public:
        |  var n: Int
        |  def this(s: Int) { n = s }
        |  def inc(): void { n = n + 1 }
        |}""".stripMargin
    def run(level: WarningLevel) = {
      val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = level, contracts = ContractMode.Check)
      new OnionCompiler(config).compileDetailed(Seq(new StreamInputSource(() => new StringReader(src), "W.on")))
    }
    val on = run(WarningLevel.On)
    assert(on.diagnostics.warnings.exists(_.category.code == "W0021"), on.diagnostics.warnings.toString)
    assert(on.diagnostics.errors.isEmpty && on.classes.nonEmpty)
    val strict = run(WarningLevel.Error)
    assert(strict.diagnostics.errors.nonEmpty, "W0021 must fail the build under --warn error")
    assert(strict.classes.isEmpty)
  }
}
