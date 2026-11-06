package onion.compiler.tools

import java.io.StringReader
import java.nio.charset.StandardCharsets
import onion.compiler.{CompilationOutcome, CompilerConfig, ContractMode, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

/**
 * `--contracts runtime` keeps a guard only for what the compiler could not prove (RFC #2557, design 7.4).
 * The guard's message is a constant in the class file, so whether the guard is there can be read from the bytes.
 */
class GuardOnlyUnprovedSpec extends AnyFunSpec {
  private def classBytes(src: String, mode: ContractMode): String = {
    val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 10, contracts = mode)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "G.on"))) match {
      case CompilationOutcome.Success(classes) =>
        new String(classes.find(_.className == "Counter").get.content, StandardCharsets.ISO_8859_1)
      case other => fail(s"did not compile: $other")
    }
  }

  private def counter(invariant: String, body: String) =
    s"""class Counter {
       |  $invariant
       |  history n >= old(n)
       |public:
       |  var n: Int
       |  def this { n = 5 }
       |  def change(): void { $body }
       |}""".stripMargin

  it("keeps the history guard when the solver cannot prove the history") {
    val bytes = classBytes(counter("invariant n >= 0", "n = n - Integer::parseInt(\"1\")"), ContractMode.Runtime)
    assert(bytes.contains("history failed"), "the guard of an unproved history must stay")
  }

  it("takes the history guard out when the solver proves the history") {
    val bytes = classBytes(counter("invariant n >= 0 && n < 1000", "n = n + 1"), ContractMode.Runtime)
    assert(!bytes.contains("history failed"), "a proved history needs no run-time guard")
  }

  it("still guards the invariant, which nothing proves yet, when the history is proved") {
    val bytes = classBytes(counter("invariant n >= 0 && n < 1000", "n = n + 1"), ContractMode.Runtime)
    assert(bytes.contains("invariant (on exit) failed"))
  }
}
