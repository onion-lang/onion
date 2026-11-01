package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilerConfig, ContractMode, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

/** W0021: the solver finds a counterexample to a history (RFC #2557, design 7.3). */
class HistoryNotProvedWarningSpec extends AnyFunSpec {
  private def w21(src: String): Seq[String] = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On, contracts = ContractMode.Check)
    val r = new OnionCompiler(config).compileDetailed(Seq(new StreamInputSource(() => new StringReader(src), "W.on")))
    assert(!r.hasErrors, r.diagnostics.toString)
    r.diagnostics.warnings.filter(_.category.code == "W0021").map(_.message)
  }

  private def counter(extra: String) =
    s"""class Counter {
       |  history n >= old(n)
       |$extra
       |public:
       |  var n: Int
       |  def this { n = 0 }
       |  def inc(): void { n = n + 1 }
       |}""".stripMargin

  it("warns that inc can overflow when n has no upper bound, naming n = 2147483647") {
    val ws = w21(counter(""))
    assert(ws.size == 1 && ws.head.contains("2147483647"), ws.toString)
  }

  it("is silent once an invariant keeps n below the maximum") {
    assert(w21(counter("  invariant n >= 0 && n < 1000000")).isEmpty)
  }
}
