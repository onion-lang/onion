package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilerConfig, ContractMode, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

/** W0026: an `ensures` that `--contracts runtime` cannot guard says so (RFC #2557, design 7.4). */
class ContractNotEnforcedWarningSpec extends AnyFunSpec {
  private def w26(src: String, mode: ContractMode = ContractMode.Runtime): Seq[String] = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On, contracts = mode)
    val r = new OnionCompiler(config).compileDetailed(Seq(new StreamInputSource(() => new StringReader(src), "W.on")))
    assert(!r.hasErrors, r.diagnostics.toString)
    r.diagnostics.warnings.filter(_.category.code == "W0026").map(_.message)
  }

  private def counter(ensures: String) =
    s"""class Counter {
       |public:
       |  var n: Int
       |  def this { n = 0 }
       |  def inc(): void
       |    ensures $ensures
       |  { n = n + 1 }
       |}""".stripMargin

  it("warns when an ensures uses old(...), which is not saved at run time yet") {
    val ws = w26(counter("n == old(n) + 1"))
    assert(ws.size == 1 && ws.head.contains("old"), ws.toString)
  }

  it("is silent when the ensures can be guarded") {
    assert(w26(counter("n >= 0")).isEmpty)
  }

  it("is silent under --contracts check, which guards nothing") {
    assert(w26(counter("n == old(n) + 1"), ContractMode.Check).isEmpty)
  }
}
