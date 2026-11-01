package onion.compiler.contracts

import onion.compiler.contracts.smt.{Rup, Sat}
import org.scalatest.funspec.AnyFunSpec
import scala.util.Random

/**
 * The SAT solver against exhaustive search (RFC #2557, S1). No external tool: for formulas small
 * enough to enumerate, the answer must match, a model must satisfy every clause, and an Unsat
 * proof must replay in the independent RUP checker.
 */
class SatFuzzSpec extends AnyFunSpec {
  private def brute(n: Int, cs: Seq[Array[Int]]): Boolean =
    (0 until (1 << n)).exists { m =>
      cs.forall(_.exists(l => (((m >> (math.abs(l) - 1)) & 1) == 1) == (l > 0)))
    }

  private def randomFormula(rnd: Random): (Int, Seq[Array[Int]]) = {
    val n = 1 + rnd.nextInt(10)
    val m = rnd.nextInt(5 * n + 2)
    val cs = Seq.fill(m) {
      val len = 1 + rnd.nextInt(3)
      Array.fill(len)((1 + rnd.nextInt(n)) * (if (rnd.nextBoolean()) 1 else -1))
    }
    (n, cs)
  }

  it("agrees with exhaustive search on 3000 random formulas") {
    val rnd = new Random(42)
    for (_ <- 1 to 3000) {
      val (n, cs) = randomFormula(rnd)
      val expected = brute(n, cs)
      Sat.solve(n, cs) match {
        case Sat.Satisfiable(model) =>
          assert(expected, s"claimed Sat on an Unsat formula: ${cs.map(_.toList)}")
          assert(Sat.satisfies(model, cs), s"model does not satisfy: ${cs.map(_.toList)}")
        case Sat.Unsatisfiable(proof) =>
          assert(!expected, s"claimed Unsat on a Sat formula: ${cs.map(_.toList)}")
          assert(Rup.check(n, cs, proof), s"proof does not replay: ${cs.map(_.toList)}")
      }
    }
  }

  it("refutes the pigeonhole principle for 4 pigeons and 3 holes, with a replayable proof") {
    val v = (p: Int, h: Int) => p * 3 + h + 1
    val cs = (0 until 4).map(p => (0 until 3).map(h => v(p, h)).toArray) ++
      (for (h <- 0 until 3; p <- 0 until 4; q <- p + 1 until 4) yield Array(-v(p, h), -v(q, h)))
    Sat.solve(12, cs) match {
      case Sat.Unsatisfiable(proof) => assert(Rup.check(12, cs, proof))
      case other                    => fail(s"expected Unsat, got $other")
    }
  }
}
