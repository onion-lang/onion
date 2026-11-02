package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilerConfig, ContractMode, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

/** An override's body against the effective contract (RFC #2557, design 4; Liskov and Wing, 1994). */
class OverrideContractSpec extends AnyFunSpec {
  private def w25(src: String): Seq[String] = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On, contracts = ContractMode.Check)
    val r = new OnionCompiler(config).compileDetailed(Seq(new StreamInputSource(() => new StringReader(src), "O.on")))
    r.diagnostics.warnings.filter(_.category.code == "W0025").map(_.message)
  }

  private def program(subBody: String, subContract: String) =
    s"""class Base {
       |public:
       |  def twice(x: Int): Int
       |    requires x >= 0 && x < 1000
       |    ensures result == x * 2
       |  { return x * 2 }
       |}
       |class Sub extends Base {
       |public:
       |  def twice(x: Int): Int
       |    $subContract
       |  { $subBody }
       |}""".stripMargin

  it("accepts an override that keeps what the base promises") {
    assert(w25(program("return x + x", "requires x >= 0 && x < 1000\n    ensures result == x * 2")).isEmpty)
  }

  it("warns about an override whose body breaks the base's postcondition") {
    val ws = w25(program("return x * 3", "requires x >= 0 && x < 1000\n    ensures result >= 0"))
    assert(ws.size == 1 && ws.head.contains("'twice'"), ws.toString)
  }

  it("says nothing about an override with no contract of its own and a base with none") {
    val src = "class Base {\npublic:\n  def f(x: Int): Int { return x }\n}\nclass Sub extends Base {\npublic:\n  def f(x: Int): Int { return x + 1 }\n}"
    assert(w25(src).isEmpty)
  }
}
