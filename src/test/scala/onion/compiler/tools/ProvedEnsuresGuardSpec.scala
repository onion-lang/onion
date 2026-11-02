package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilationOutcome, CompilerConfig, ContractMode, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

/** `--contracts runtime` keeps a runtime check only for an `ensures` that was not proved (RFC #2557, 7.4). */
class ProvedEnsuresGuardSpec extends AnyFunSpec {
  private def classText(src: String, mode: ContractMode): String = {
    val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 10, contracts = mode)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "G.on"))) match {
      case CompilationOutcome.Success(cs) => new String(cs.find(_.className == "M").get.content, "ISO-8859-1")
      case other                          => fail(s"did not compile: $other")
    }
  }

  private def src(ensures: String, body: String) =
    s"class M {\npublic:\n  def f(x: Int): Int\n    requires x >= 0 && x < 100\n    ensures $ensures\n  { $body }\n}"

  it("drops the guard of an ensures the solver proved") {
    assert(!classText(src("result >= x", "return x * 2"), ContractMode.Runtime).contains("postcondition failed"))
  }

  it("keeps the guard of an ensures that was not proved") {
    // Math.abs is not modelled, so the solver cannot prove this one
    val unproved = src("result >= 0", "return Math::abs(x)")
    assert(classText(unproved, ContractMode.Runtime).contains("postcondition failed"))
  }

  it("keeps the guard of an ensures the solver refuted") {
    assert(classText(src("result < x", "return x * 2"), ContractMode.Runtime).contains("postcondition failed"))
  }
}
