package onion.compiler.tools

import java.io.{ByteArrayOutputStream, PrintStream, StringReader}
import onion.compiler.{CompilerConfig, ContractMode, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

/** A history proved through a call: the callee's contract stands in for its body (RFC #2557, design 7.3). */
class HistoryCallProofSpec extends AnyFunSpec {
  private def compile(src: String): (Seq[String], String) = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, verbose = true, contracts = ContractMode.Check)
    val buffer = new ByteArrayOutputStream()
    val old = System.err
    System.setErr(new PrintStream(buffer, true, "UTF-8"))
    try {
      val r = new OnionCompiler(config).compileDetailed(Seq(new StreamInputSource(() => new StringReader(src), "P.on")))
      (r.diagnostics.errors.flatMap(_.errorCode), buffer.toString("UTF-8"))
    } finally System.setErr(old)
  }

  private def counter(stepEnsures: String) =
    s"""class Counter {
       |  invariant n >= 0 && n < 1000
       |  history n >= old(n)
       |public:
       |  var n: Int
       |  def this { n = 0 }
       |  def step(x: Int): Int
       |    requires x >= 0 && x < 100
       |    ensures $stepEnsures
       |  { return x }
       |  def add(k: Int): void
       |    requires k >= 0 && k < 100
       |  { if n < 1000 { n = n + step(k) } }
       |}""".stripMargin

  it("proves the history through the call and says so under --verbose") {
    val (errors, err) = compile(counter("result >= 0 && result < 100"))
    assert(errors.isEmpty, errors.toString)
    assert(err.contains("[history] proved: Counter.add: n >= old(n)"), err)
  }

  it("reports a history the callee's contract cannot keep (E0112 or W0021)") {
    val (errors, err) = compile(counter("result > -100 && result < 100"))
    assert(!err.contains("[history] proved: Counter.add"), err)
  }
}
