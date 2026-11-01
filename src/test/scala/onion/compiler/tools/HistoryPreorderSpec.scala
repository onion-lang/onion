package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilationOutcome, CompilerConfig, ContractMode, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

/**
 * A `history` must be a preorder (RFC #2557, design 3.3): reflexive and transitive, so that
 * checking one call at a time checks the whole life of the object. Assertions use error codes.
 */
class HistoryPreorderSpec extends AnyFunSpec {

  private def codes(history: String): Seq[String] = {
    val src =
      s"""class C {
         |  var n: Int
         |  history $history
         |public:
         |  def get(): Int { return n }
         |}""".stripMargin
    val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 10, checkLaws = false, contracts = ContractMode.Check)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "C.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.flatMap(_.errorCode)
      case _                                => Nil
    }
  }

  describe("a history that is a preorder") {
    for (h <- Seq("n >= old(n)", "n <= old(n)", "n == old(n)", "old(n) <= n", "n >= old(n) && n >= 0"))
      it(s"accepts `$h`") { assert(!codes(h).contains("E0108")) }
  }

  describe("a history that is not a preorder") {
    for (h <- Seq("n == old(n) + 1", "n > old(n)", "n < old(n)", "n != old(n)", "n >= old(n) + 1", "n >= old(n) - 1"))
      it(s"rejects `$h`") { assert(codes(h).contains("E0108")) }
  }

  describe("a history the check cannot classify") {
    it("is left to the prover and not reported") {
      assert(!codes("n * n >= old(n)").contains("E0108"))
    }
  }
}
