package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilerConfig, ContractMode, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

/** `@Trusted` (RFC #2557): a contract relied on without being verified is listed, never silent. */
class TrustedContractSpec extends AnyFunSpec {
  private def w27(src: String): Seq[String] = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On, contracts = ContractMode.Check)
    val r = new OnionCompiler(config).compileDetailed(Seq(new StreamInputSource(() => new StringReader(src), "T.on")))
    assert(!r.hasErrors, r.diagnostics.toString)
    r.diagnostics.warnings.filter(_.category.code == "W0027").map(_.message)
  }

  it("lists a @Trusted method that states a contract") {
    val src =
      """class Math2 {
        |public:
        |  @Trusted
        |  def double(x: Int): Int
        |    requires x >= 0
        |    ensures result >= x
        |  { return x * 2 }
        |}""".stripMargin
    val ws = w27(src)
    assert(ws.size == 1 && ws.head.contains("'double'"), ws.toString)
  }

  it("says nothing about a method that is not @Trusted") {
    val src =
      """class Math2 {
        |public:
        |  def double(x: Int): Int
        |    requires x >= 0
        |    ensures result >= x
        |  { return x * 2 }
        |}""".stripMargin
    assert(w27(src).isEmpty)
  }

  it("says nothing about a @Trusted method with no contract: there is nothing to trust") {
    val src = "class Math2 {\npublic:\n  @Trusted\n  def double(x: Int): Int { return x * 2 }\n}"
    assert(w27(src).isEmpty)
  }
}
