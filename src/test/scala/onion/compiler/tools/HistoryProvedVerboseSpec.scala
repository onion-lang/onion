package onion.compiler.tools

import java.io.{ByteArrayOutputStream, PrintStream, StringReader}
import onion.compiler.{CompilerConfig, ContractMode, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

/** `--verbose` lists the histories the solver proved (RFC #2557); without it, a proof prints nothing. */
class HistoryProvedVerboseSpec extends AnyFunSpec {
  private val src =
    """class Counter {
      |  invariant n >= 0 && n < 1000000
      |  history n >= old(n)
      |public:
      |  var n: Int
      |  def this { n = 0 }
      |  def inc(): void { n = n + 1 }
      |}""".stripMargin

  private def stderrOf(verbose: Boolean): String = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, verbose = verbose, contracts = ContractMode.Check)
    val buffer = new ByteArrayOutputStream()
    val old = System.err
    System.setErr(new PrintStream(buffer, true, "UTF-8"))
    try new OnionCompiler(config).compileDetailed(Seq(new StreamInputSource(() => new StringReader(src), "P.on")))
    finally System.setErr(old)
    buffer.toString("UTF-8")
  }

  it("names the proved history under --verbose") {
    assert(stderrOf(verbose = true).contains("[history] proved: Counter.inc: n >= old(n)"))
  }

  it("prints nothing about a proof without --verbose") {
    assert(!stderrOf(verbose = false).contains("[history]"))
  }
}
