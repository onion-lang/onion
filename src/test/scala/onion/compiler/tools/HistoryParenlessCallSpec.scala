package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilerConfig, ContractMode, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

/** `history snap >= old(snap)` calls the method `snap`; it is checked like `snap() >= old(snap())` (#3033). */
class HistoryParenlessCallSpec extends AnyFunSpec {
  private def w24(src: String): Seq[String] = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On, contracts = ContractMode.Check)
    val r = new OnionCompiler(config).compileDetailed(Seq(new StreamInputSource(() => new StringReader(src), "H.on")))
    assert(!r.hasErrors, r.diagnostics.toString)
    r.diagnostics.warnings.filter(_.category.code == "W0024").map(_.message)
  }

  private def counter(history: String): String =
    s"""class Counter {
       |  var total: Int
       |  var hits: Int
       |  history $history
       |public:
       |  def this { total = 0; hits = 0 }
       |  def snap: Int = total
       |  def add(k: Int): void { total = total - k }
       |}""".stripMargin

  it("warns for a paren-less method call, as for the call with parentheses") {
    val bare = w24(counter("snap >= old(snap)"))
    val parens = w24(counter("snap() >= old(snap())"))
    assert(parens.size == 1, parens.toString)
    assert(bare.size == 1, bare.toString)
    assert(bare.head.contains("add"), bare.toString)
  }

  it("warns for `this.snap >= old(this.snap)`") {
    val ws = w24(counter("this.snap >= old(this.snap)"))
    assert(ws.size == 1, ws.toString)
  }

  it("warns for a paren-less call conjoined with a field relation") {
    val ws = w24(counter("snap >= old(snap) && hits >= old(hits)"))
    assert(ws.exists(_.contains("add")), ws.toString)
  }

  it("still checks a real field relation by syntax: no W0024 for a proved field") {
    val src =
      """class Up {
        |  var total: Int
        |  history total >= old(total)
        |public:
        |  def this { total = 0 }
        |  def keep(): void { total = total }
        |}""".stripMargin
    assert(w24(src).isEmpty)
  }
}
