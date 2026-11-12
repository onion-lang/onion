package onion.compiler.tools

import java.io.{ByteArrayOutputStream, PrintStream, StringReader}
import onion.compiler.{CompilerConfig, ContractMode, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

/** Every method's `ensures` is proved against its body (RFC #2557, design 6). */
class EnsuresVerifySpec extends AnyFunSpec {
  private def run(src: String) = {
    val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 10, verbose = true,
      warningLevel = WarningLevel.On, contracts = ContractMode.Check)
    val buffer = new ByteArrayOutputStream()
    val old = System.err
    System.setErr(new PrintStream(buffer, true, "UTF-8"))
    try {
      val r = new OnionCompiler(config).compileDetailed(Seq(new StreamInputSource(() => new StringReader(src), "E.on")))
      (r.diagnostics.warnings.filter(_.category.code == "W0028"), buffer.toString("UTF-8"))
    } finally System.setErr(old)
  }

  private def cls(body: String, ann: String = "") =
    s"class M {\npublic:\n  $ann\n  def f(x: Int): Int\n    requires x >= 0 && x < 100\n    ensures $body\n  { return x * 2 }\n}"

  it("reports a proved ensures under --verbose") {
    val (ws, err) = run(cls("result >= x"))
    assert(ws.isEmpty)
    assert(err.contains("[ensures] proved: M.f"), err)
  }

  it("warns W0028 with a counterexample when the body can break the ensures") {
    val (ws, _) = run(cls("result < x"))
    assert(ws.size == 1 && ws.head.message.contains("'f'"), ws.toString)
  }

  it("does not check a @Trusted method") {
    val (ws, err) = run(cls("result < x", "@Trusted"))
    assert(ws.isEmpty && !err.contains("[ensures] proved"), err)
  }

  describe("an ensures that needs a class invariant (#2672)") {
    // Nothing checks the invariant, so a constructor can break it: the proof is not "proved".
    val counter =
      "class Counter {\n  invariant n >= 0 && n < 10\nprotected:\n  var n: Int\npublic:\n" +
        "  def this { n = 2147483647 }\n  def next(): Int ensures result > 0 { return n + 1 }\n}"

    it("is not reported as proved") {
      val (ws, err) = run(counter)
      assert(!err.contains("[ensures] proved"), err)
      assert(ws.isEmpty, ws.toString)
    }

    it("is still proved when the invariant is not needed") {
      val (_, err) = run(counter.replace("ensures result > 0 { return n + 1 }", "ensures result == 1 { return 1 }"))
      assert(err.contains("[ensures] proved: Counter.next"), err)
    }
  }
}
