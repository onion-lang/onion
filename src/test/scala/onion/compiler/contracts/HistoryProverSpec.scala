package onion.compiler.contracts

import onion.compiler.AST
import onion.compiler.parser.OnionParser
import org.scalatest.funspec.AnyFunSpec

/** Proving a history for one method with the bit-vector solver (RFC #2557, design 7.3). */
class HistoryProverSpec extends AnyFunSpec {
  private def prove(body: String, invariant: String = "", requires: String = "", op: String = ">=",
                    history: String = "n >= old(n)"): HistoryProver.Outcome = {
    val inv = if (invariant.isEmpty) "" else s"  invariant $invariant\n"
    val src =
      s"""class C {
         |  history $history
         |$inv  var n: Int
         |public:
         |  def f(k: Int): void $requires { $body }
         |}""".stripMargin
    val cls = OnionParser.parse(src).toplevels.collectFirst { case c: AST.ClassDeclaration => c }.get
    val m = (cls.defaultSection.toSeq ++ cls.sections).flatMap(_.members).collectFirst { case m: AST.MethodDeclaration => m }.get
    HistoryProver.prove(m, "n", op, Set("n"), cls.invariants)
  }

  it("proves n = n + k when k is known to be non-negative and the sum cannot overflow") {
    assert(prove("n = n + k", invariant = "n >= 0 && n < 1000", requires = "requires k >= 0 && k < 1000") == HistoryProver.Proved)
  }

  it("finds the overflow counterexample for n = n + 1 with no bound on n") {
    prove("n = n + 1") match {
      case HistoryProver.Counterexample(before) => assert(before("n") == Int.MaxValue)
      case other                                => fail(s"expected a counterexample, got $other")
    }
  }

  it("proves n = n + 1 once an invariant keeps n below the maximum") {
    assert(prove("n = n + 1", invariant = "n < 1000000") == HistoryProver.Proved)
  }

  it("finds the counterexample for n = 0 against n >= old(n)") {
    prove("n = 0") match {
      case HistoryProver.Counterexample(before) => assert(before("n") > 0)
      case other                                => fail(s"expected a counterexample, got $other")
    }
  }

  it("proves a branch: if k > 0 then raise n else leave it") {
    assert(prove("if k > 0 { n = n + 1 }", invariant = "n < 1000") == HistoryProver.Proved)
  }

  it("proves n == old(n) for a method that does not write n") {
    assert(prove("", op = "==", history = "n == old(n)") == HistoryProver.Proved)
  }

  it("says Unsupported for what it does not model (a call), never a guess") {
    assert(prove("println(\"x\")") == HistoryProver.Unsupported)
  }
}
