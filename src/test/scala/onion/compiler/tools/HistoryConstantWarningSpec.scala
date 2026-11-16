package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilerConfig, ContractMode, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

/** A `history` that reads no field and calls no method cannot be broken by a write (#2901). */
class HistoryConstantWarningSpec extends AnyFunSpec {
  private def w24(src: String): Seq[String] = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On, contracts = ContractMode.Check)
    val r = new OnionCompiler(config).compileDetailed(Seq(new StreamInputSource(() => new StringReader(src), "H.on")))
    assert(!r.hasErrors, r.diagnostics.toString)
    r.diagnostics.warnings.filter(_.category.code == "W0024").map(_.message)
  }

  it("stays silent for a constant history on a class that writes a field") {
    val src =
      """class ConstHist {
        |  var total: Int
        |  history 1 >= 0
        |public:
        |  def this { total = 0 }
        |  def add(k: Int): void { total = total + k }
        |}""".stripMargin
    assert(w24(src).isEmpty)
  }

  it("still warns for a history over a method call") {
    val src =
      """class CallHist {
        |  var total: Int
        |  history x() >= old(x())
        |public:
        |  def this { total = 0 }
        |  def x(): Int = total
        |  def add(k: Int): void { total = k }
        |}""".stripMargin
    assert(w24(src).size == 1)
  }
}
