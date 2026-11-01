package onion.compiler.contracts

import onion.compiler.contracts.smt.{BitBlast, Rup, Sat}
import org.scalatest.funspec.AnyFunSpec
import scala.util.Random

/**
 * Bit-blasting against direct evaluation (RFC #2557, S2). Random 4-bit expressions over two
 * variables: the solver's verdict must match an enumeration of all 256 assignments, and a model
 * must evaluate to the asserted value.
 */
class BitBlastFuzzSpec extends AnyFunSpec {
  private val W = 4
  private val M = (1 << W) - 1

  private sealed trait E
  private case object X extends E
  private case object Y extends E
  private final case class K(v: Int) extends E
  private final case class Bin(op: String, l: E, r: E) extends E
  private final case class Un(op: String, e: E) extends E
  private final case class If(cmp: String, a: E, b: E, t: E, e: E) extends E

  private def eval(e: E, x: Int, y: Int): Int = e match {
    case X => x
    case Y => y
    case K(v) => v & M
    case Bin(op, l, r) =>
      val a = eval(l, x, y); val b = eval(r, x, y)
      (op match { case "+" => a + b; case "-" => a - b; case "*" => a * b; case "&" => a & b; case "|" => a | b; case _ => a ^ b }) & M
    case Un("neg", s) => (-eval(s, x, y)) & M
    case Un(_, s) => (~eval(s, x, y)) & M
    case If(c, a, b, t, f) =>
      val av = eval(a, x, y); val bv = eval(b, x, y)
      def sgn(v: Int) = if (v >= (1 << (W - 1))) v - (1 << W) else v
      val cond = if (c == "ult") av < bv else sgn(av) < sgn(bv)
      if (cond) eval(t, x, y) else eval(f, x, y)
  }

  private def blast(bb: BitBlast, e: E, x: bb.Word, y: bb.Word): bb.Word = e match {
    case X => x
    case Y => y
    case K(v) => bb.const(W, v.toLong)
    case Bin(op, l, r) =>
      val a = blast(bb, l, x, y); val b = blast(bb, r, x, y)
      op match { case "+" => bb.add(a, b); case "-" => bb.sub(a, b); case "*" => bb.mul(a, b)
                 case "&" => bb.bitAnd(a, b); case "|" => bb.bitOr(a, b); case _ => bb.bitXor(a, b) }
    case Un("neg", s) => bb.neg(blast(bb, s, x, y))
    case Un(_, s) => bb.bitNot(blast(bb, s, x, y))
    case If(c, a, b, t, f) =>
      val av = blast(bb, a, x, y); val bv = blast(bb, b, x, y)
      val cond = if (c == "ult") bb.ult(av, bv) else bb.slt(av, bv)
      bb.mux(cond, blast(bb, t, x, y), blast(bb, f, x, y))
  }

  private def gen(rnd: Random, depth: Int): E =
    if (depth == 0) rnd.nextInt(3) match { case 0 => X; case 1 => Y; case _ => K(rnd.nextInt(16)) }
    else rnd.nextInt(8) match {
      case 0 | 1 | 2 | 3 =>
        Bin(Seq("+", "-", "*", "&", "|", "^")(rnd.nextInt(6)), gen(rnd, depth - 1), gen(rnd, depth - 1))
      case 4 => Un("neg", gen(rnd, depth - 1))
      case 5 => Un("not", gen(rnd, depth - 1))
      case _ => If(Seq("ult", "slt")(rnd.nextInt(2)), gen(rnd, depth - 1), gen(rnd, depth - 1), gen(rnd, depth - 1), gen(rnd, depth - 1))
    }

  it("agrees with enumeration on 1500 random 4-bit expressions") {
    val rnd = new Random(7)
    for (_ <- 1 to 1500) {
      val e = gen(rnd, 1 + rnd.nextInt(3))
      val target = rnd.nextInt(16)
      val bb = new BitBlast
      val x = bb.fresh(W); val y = bb.fresh(W)
      bb.assertTrue(bb.eq(blast(bb, e, x, y), bb.const(W, target.toLong)))
      val expected = (for (a <- 0 to M; b <- 0 to M) yield eval(e, a, b) == target).contains(true)
      bb.solve() match {
        case Sat.Satisfiable(model) =>
          assert(expected, s"claimed Sat: $e == $target")
          assert(eval(e, bb.valueOf(model, x).toInt, bb.valueOf(model, y).toInt) == target, s"bad model for $e == $target")
        case Sat.Unsatisfiable(proof) =>
          assert(!expected, s"claimed Unsat: $e == $target")
          assert(Rup.check(bb.numVars, bb.allClauses, proof), s"proof does not replay: $e == $target")
      }
    }
  }

  it("proves x + y == y + x for 8-bit words (no counterexample)") {
    val bb = new BitBlast
    val x = bb.fresh(8); val y = bb.fresh(8)
    bb.assertTrue(-bb.eq(bb.add(x, y), bb.add(y, x)))
    bb.solve() match {
      case Sat.Unsatisfiable(proof) => assert(Rup.check(bb.numVars, bb.allClauses, proof))
      case other                    => fail(s"expected Unsat, got $other")
    }
  }

  it("finds the counterexample that n - 1 can be below n, wrapping at the minimum int") {
    val bb = new BitBlast
    val n = bb.fresh(32)
    // n - 1 > n (signed) happens only when n is the minimum value.
    bb.assertTrue(bb.slt(n, bb.sub(n, bb.const(32, 1))))
    bb.solve() match {
      case Sat.Satisfiable(model) => assert(bb.valueOf(model, n) == 0x80000000L)
      case other                  => fail(s"expected Sat, got $other")
    }
  }
}
