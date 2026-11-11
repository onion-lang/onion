package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilationOutcome, CompilerConfig, ContractMode, OnionCompiler, StreamInputSource}
import onion.compiler.contracts.smt.Sat
import org.scalatest.funspec.AnyFunSpec

/** The `ensures` prover never hangs the build (#2748). */
class EnsuresProverBudgetSpec extends AnyFunSpec {
  private val acct =
    """class Acct {
      |  var bal: Int
      |public:
      |  def this(b: Int) { bal = b }
      |  def withdraw(n: Int): Int
      |    requires n > 0 && n <= bal
      |    ensures result >= 0
      |  { bal = bal - n; return bal }
      |}
      |val a = new Acct(10)
      |println(a.withdraw(3))
      |""".stripMargin

  /** Compiles on a daemon thread, so a hang fails the test instead of the suite. */
  private def compileWithin(seconds: Int, src: String, mode: ContractMode): CompilationOutcome = {
    @volatile var outcome: Option[CompilationOutcome] = None
    val t = new Thread(() => {
      val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 10, contracts = mode)
      outcome = Some(new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "Acct.on"))))
    })
    t.setDaemon(true)
    t.start()
    t.join(seconds * 1000L)
    outcome.getOrElse(fail(s"compiling under --contracts ${ContractMode.flagName(mode)} did not finish in $seconds s"))
  }

  private def classText(outcome: CompilationOutcome): String = outcome match {
    case CompilationOutcome.Success(cs) => new String(cs.find(_.className == "Acct").get.content, "ISO-8859-1")
    case other                          => fail(s"did not compile: $other")
  }

  it("compiles a requires/ensures method under --contracts check without hanging") {
    assert(compileWithin(30, acct, ContractMode.Check).isInstanceOf[CompilationOutcome.Success])
  }

  it("runs no prover under --contracts off: the clause is rejected at once") {
    assert(compileWithin(30, acct, ContractMode.Off).isInstanceOf[CompilationOutcome.Failure])
  }

  it("proves bal - n >= 0 from n > 0 && n <= bal, so runtime drops the guard") {
    assert(!classText(compileWithin(30, acct, ContractMode.Runtime)).contains("postcondition failed"))
  }

  it("gives up on a formula past its conflict budget instead of searching forever") {
    // Pigeonhole: 9 pigeons, 8 holes. Unsatisfiable, and hard for any CDCL solver.
    val (p, h) = (9, 8)
    def v(i: Int, j: Int) = i * h + j + 1
    val atLeastOne = (0 until p).map(i => (0 until h).map(j => v(i, j)).toArray)
    val atMostOne = for (j <- 0 until h; a <- 0 until p; b <- a + 1 until p) yield Array(-v(a, j), -v(b, j))
    assert(Sat.solve(p * h, atLeastOne ++ atMostOne, maxConflicts = 1000) == Sat.Unknown)
  }
}
