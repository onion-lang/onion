package onion.compiler.contracts

import onion.compiler.AST
import onion.compiler.parser.OnionParser
import org.scalatest.funspec.AnyFunSpec

/** Proving a history for one method with the bit-vector solver (RFC #2557, design 7.3). */
class HistoryProverSpec extends AnyFunSpec {
  private def prove(body: String, invariant: String = "", requires: String = "", op: String = ">=",
                    history: String = "n >= old(n)", params: String = "k: Int"): HistoryProver.Outcome = {
    val inv = if (invariant.isEmpty) "" else s"  invariant $invariant\n"
    val src =
      s"""class C {
         |  history $history
         |$inv  var n: Int
         |public:
         |  def f($params): void $requires { $body }
         |}""".stripMargin
    val cls = OnionParser.parse(src).toplevels.collectFirst { case c: AST.ClassDeclaration => c }.get
    val m = (cls.defaultSection.toSeq ++ cls.sections).flatMap(_.members).collectFirst { case m: AST.MethodDeclaration => m }.get
    HistoryProver.prove(m, "n", op, Set("n"), cls.invariants)
  }

  // #2602: a proof must hold for every run, or there is no proof.
  it("refuses a parameter named like the field: this.n = n is not a no-op") {
    assert(prove("this.n = n", invariant = "n >= 0 && n < 1000", params = "n: Int") == HistoryProver.Unsupported)
  }

  it("refuses a return that ends only an if branch: the rest of the body is skipped on that path") {
    val outcome = prove("if k > 0 { n = n - k\n return }\n    n = n + k", invariant = "n >= 0 && n < 1000",
      requires = "requires k >= 0 && k < 1000")
    assert(outcome != HistoryProver.Proved)
  }

  it("does not take one branch's return value for the method's result") {
    val src =
      """class C {
        |public:
        |  var n: Int
        |  def f(k: Int): Int ensures result == 2 { if k > 0 { return 1 } else { return 2 } }
        |}""".stripMargin
    val cls = OnionParser.parse(src).toplevels.collectFirst { case c: AST.ClassDeclaration => c }.get
    val m = (cls.defaultSection.toSeq ++ cls.sections).flatMap(_.members).collectFirst { case m: AST.MethodDeclaration => m }.get
    assert(HistoryProver.proveEnsures(m, m.ensures, Nil, Set("n")) != HistoryProver.Proved)
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

  it("models an Int local: val d = k * 2; n = n + d") {
    assert(prove("val d = k * 2\n    n = n + d", invariant = "n >= 0 && n < 1000", requires = "requires k >= 0 && k < 100") == HistoryProver.Proved)
  }

  it("models a trailing return") {
    assert(prove("n = n + 1\n    return", invariant = "n < 1000") == HistoryProver.Proved)
  }

  it("refuses a local that shadows a field") {
    assert(prove("val n = 5\n    n = 6") == HistoryProver.Unsupported)
  }

  private def proveWithHelper(helper: String, body: String, invariant: String = "n >= 0 && n < 1000", fRequires: String = ""): HistoryProver.Outcome = {
    val src =
      s"""class C {
         |  invariant $invariant
         |  history n >= old(n)
         |public:
         |  var n: Int
         |$helper
         |  def f(k: Int): void $fRequires { $body }
         |}""".stripMargin
    val cls = OnionParser.parse(src).toplevels.collectFirst { case c: AST.ClassDeclaration => c }.get
    val methods = (cls.defaultSection.toSeq ++ cls.sections).flatMap(_.members).collect { case m: AST.MethodDeclaration => m }
    HistoryProver.prove(methods.find(_.name == "f").get, "n", ">=", Set("n"), cls.invariants, methods.map(m => m.name -> m).toMap)
  }

  it("uses a pure callee's contract: n = n + step(k) is proved once k satisfies step's requires") {
    val helper = "  def step(x: Int): Int\n    requires x >= 0 && x < 100\n    ensures result >= 0 && result < 100\n  { return x }"
    assert(proveWithHelper(helper, "n = n + step(k)", fRequires = "requires k >= 0 && k < 100") == HistoryProver.Proved)
  }

  it("finds the counterexample when the callee's contract allows a negative result") {
    val helper = "  def step(x: Int): Int\n    requires x >= 0 && x < 100\n    ensures result > -100 && result < 100\n  { return x }"
    assert(proveWithHelper(helper, "n = n + step(k)", fRequires = "requires k >= 0 && k < 100")
      .isInstanceOf[HistoryProver.Counterexample])
  }

  it("does not use a callee that states no contract (Unsupported, never a guess)") {
    val helper = "  def step(x: Int): Int { return x }"
    assert(proveWithHelper(helper, "n = n + step(k)") == HistoryProver.Unsupported)
  }

  it("refuses the call when the callee's requires can fail at the call site") {
    val helper = "  def step(x: Int): Int\n    requires x >= 0\n    ensures result >= 0\n  { return x }"
    // k is unconstrained, so step's requires (x >= 0) is not established: the call is not understood
    assert(proveWithHelper(helper, "n = n + step(k)") == HistoryProver.Unsupported)
  }
}
