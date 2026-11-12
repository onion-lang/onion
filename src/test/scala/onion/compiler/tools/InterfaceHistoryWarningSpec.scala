package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilerConfig, ContractMode, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

/** An interface `history` over method calls names no field; a write that may break it is W0024 (#2735). */
class InterfaceHistoryWarningSpec extends AnyFunSpec {
  private def w24(src: String): Seq[String] = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On, contracts = ContractMode.Check)
    val r = new OnionCompiler(config).compileDetailed(Seq(new StreamInputSource(() => new StringReader(src), "H.on")))
    assert(!r.hasErrors, r.diagnostics.toString)
    r.diagnostics.warnings.filter(_.category.code == "W0024").map(_.message)
  }

  private val impl =
    """class IfaceCounter conforms IfaceWatched {
      |  var v: Int
      |public:
      |  def this { v = 0 }
      |  def x(): Int = v
      |  def setX(k: Int): void { v = k }
      |}""".stripMargin

  it("warns at a method that writes a field when the interface history names only method calls") {
    val src = "interface IfaceWatched {\n  def x(): Int\n  history x() >= old(x())\n}\n" + impl
    val ws = w24(src)
    assert(ws.size == 1, ws.toString)
    assert(ws.head.contains("setX"))
  }

  it("stays silent when no method writes a field") {
    val src = "interface IfaceWatched {\n  def x(): Int\n  history x() >= old(x())\n}\n" +
      "class IfaceReader conforms IfaceWatched {\n  val v: Int\npublic:\n  def this { v = 0 }\n  def x(): Int = v\n}"
    assert(w24(src).isEmpty)
  }
}
