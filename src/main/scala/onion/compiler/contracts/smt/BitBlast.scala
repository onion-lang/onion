package onion.compiler.contracts.smt

import scala.collection.mutable

/**
 * Bit-vector arithmetic as a circuit of gates, turned into clauses by the Tseitin encoding
 * (RFC #2557, solver stage S2). A word is `width` literals, least significant bit first, and every
 * operation wraps modulo 2^width, as a JVM `int` or `long` does.
 *
 * Usage: build words with [[fresh]] and [[const]], combine them, [[assertTrue]] a literal, then
 * [[solve]]. The model of a word is read back with [[valueOf]].
 */
final class BitBlast {
  private var nextVar = 1
  private val clauses = mutable.ArrayBuffer.empty[Array[Int]]
  private val fixedTrue: Int = { val v = newVar(); clauses += Array(v); v }
  val tru: Int = fixedTrue
  val fls: Int = -fixedTrue

  type Word = Array[Int]

  private def newVar(): Int = { val v = nextVar; nextVar += 1; v }
  def numVars: Int = nextVar - 1
  def allClauses: Seq[Array[Int]] = clauses.toSeq

  // ---- gates -------------------------------------------------------------------------------
  def not(a: Int): Int = -a

  def and(a: Int, b: Int): Int =
    if (a == fls || b == fls) fls else if (a == tru) b else if (b == tru) a else if (a == b) a else if (a == -b) fls
    else { val o = newVar(); clauses += Array(-o, a); clauses += Array(-o, b); clauses += Array(o, -a, -b); o }

  def or(a: Int, b: Int): Int = -and(-a, -b)

  def xor(a: Int, b: Int): Int =
    if (a == fls) b else if (b == fls) a else if (a == tru) -b else if (b == tru) -a else if (a == b) fls else if (a == -b) tru
    else {
      val o = newVar()
      clauses += Array(-o, a, b); clauses += Array(-o, -a, -b)
      clauses += Array(o, -a, b); clauses += Array(o, a, -b)
      o
    }

  def ite(c: Int, t: Int, e: Int): Int = or(and(c, t), and(-c, e))

  // ---- words -------------------------------------------------------------------------------
  def fresh(width: Int): Word = Array.fill(width)(newVar())

  def const(width: Int, value: Long): Word = Array.tabulate(width)(i => if (((value >>> i) & 1L) == 1L) tru else fls)

  def bitAnd(a: Word, b: Word): Word = a.zip(b).map { case (x, y) => and(x, y) }
  def bitOr(a: Word, b: Word): Word = a.zip(b).map { case (x, y) => or(x, y) }
  def bitXor(a: Word, b: Word): Word = a.zip(b).map { case (x, y) => xor(x, y) }
  def bitNot(a: Word): Word = a.map(-_)

  /** Sum and carry-out of `a + b + carryIn`. */
  private def addCarry(a: Word, b: Word, carryIn: Int): (Word, Int) = {
    var carry = carryIn
    val out = new Array[Int](a.length)
    for (i <- a.indices) {
      val s = xor(xor(a(i), b(i)), carry)
      carry = or(and(a(i), b(i)), and(carry, xor(a(i), b(i))))
      out(i) = s
    }
    (out, carry)
  }

  def add(a: Word, b: Word): Word = addCarry(a, b, fls)._1
  def sub(a: Word, b: Word): Word = addCarry(a, bitNot(b), tru)._1
  def neg(a: Word): Word = sub(const(a.length, 0), a)

  def mul(a: Word, b: Word): Word = {
    val w = a.length
    var acc = const(w, 0)
    for (i <- 0 until w) {
      val shifted = Array.tabulate(w)(j => if (j < i) fls else a(j - i))
      acc = add(acc, shifted.map(and(_, b(i))))
    }
    acc
  }

  def eq(a: Word, b: Word): Int = a.zip(b).foldLeft(tru) { case (acc, (x, y)) => and(acc, -xor(x, y)) }

  /** Unsigned `a < b`: the borrow out of `a - b`. */
  def ult(a: Word, b: Word): Int = -addCarry(a, bitNot(b), tru)._2

  /** Signed `a < b`: flip the sign bits and compare unsigned. */
  def slt(a: Word, b: Word): Int = {
    val w = a.length
    ult(a.updated(w - 1, -a(w - 1)), b.updated(w - 1, -b(w - 1)))
  }

  def mux(c: Int, t: Word, e: Word): Word = t.zip(e).map { case (x, y) => ite(c, x, y) }

  // ---- solving -----------------------------------------------------------------------------
  def assertTrue(lit: Int): Unit = clauses += Array(lit)

  def solve(): Sat.Result = Sat.solve(numVars, clauses.toSeq)

  /** The unsigned value of `w` in `model`. */
  def valueOf(model: Array[Boolean], w: Word): Long =
    w.indices.foldLeft(0L) { (acc, i) =>
      val l = w(i)
      val bit = if (l > 0) model(l) else !model(-l)
      if (bit) acc | (1L << i) else acc
    }
}
