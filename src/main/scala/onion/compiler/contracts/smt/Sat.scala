package onion.compiler.contracts.smt

import scala.collection.mutable

/**
 * A small CDCL SAT solver (RFC #2557, solver stage S1): two watched literals, first-UIP clause
 * learning, non-chronological backjumping, VSIDS-style activity and restarts. Pure JVM, no
 * dependencies.
 *
 * Literals are non-zero ints in DIMACS style: `v` is variable `v` true, `-v` false.
 * An answer is never trusted on its own: a `Sat` model is checked against the clauses by the
 * caller ([[Sat.satisfies]]), and an `Unsat` carries the learned clauses as a RUP proof that
 * [[Rup.check]] replays independently.
 */
object Sat {
  sealed trait Result
  final case class Satisfiable(model: Array[Boolean]) extends Result // index 1..numVars
  final case class Unsatisfiable(proof: Seq[Array[Int]]) extends Result // learned clauses, last is empty

  def solve(numVars: Int, clauses: Seq[Array[Int]]): Result = new Solver(numVars, clauses).run()

  /** Whether `model` (index 1..n) makes every clause true. */
  def satisfies(model: Array[Boolean], clauses: Seq[Array[Int]]): Boolean =
    clauses.forall(_.exists(l => if (l > 0) model(l) else !model(-l)))

  private final class Solver(n: Int, input: Seq[Array[Int]]) {
    private val clauses = mutable.ArrayBuffer.empty[Array[Int]]
    private val watches = Array.fill(2 * n + 2)(mutable.ArrayBuffer.empty[Int])
    private val value = new Array[Byte](n + 1) // 0 unassigned, 1 true, -1 false
    private val level = new Array[Int](n + 1)
    private val reason = Array.fill[Int](n + 1)(-1)
    private val trail = mutable.ArrayBuffer.empty[Int]
    private val trailLim = mutable.ArrayBuffer.empty[Int]
    private var qhead = 0
    private val activity = new Array[Double](n + 1)
    private var bump = 1.0
    private val proof = mutable.ArrayBuffer.empty[Array[Int]]
    private val phase = new Array[Boolean](n + 1)

    private def idx(l: Int): Int = if (l > 0) 2 * l else 2 * (-l) + 1
    private def valueOf(l: Int): Int = { val v = value(math.abs(l)); if (l > 0) v else -v }

    private def enqueue(l: Int, from: Int): Boolean = valueOf(l) match {
      case 1 => true
      case -1 => false
      case _ =>
        val v = math.abs(l)
        value(v) = if (l > 0) 1 else -1
        level(v) = trailLim.size
        reason(v) = from
        trail += l
        true
    }

    private def attach(c: Array[Int]): Int = {
      clauses += c
      val id = clauses.size - 1
      if (c.length >= 2) { watches(idx(c(0))) += id; watches(idx(c(1))) += id }
      id
    }

    /** Boolean constraint propagation; the index of a conflicting clause, or -1. */
    private def propagate(): Int = {
      while (qhead < trail.size) {
        val p = trail(qhead); qhead += 1
        val falseLit = -p
        val ws = watches(idx(falseLit))
        var i = 0
        var j = 0
        var conflict = -1
        while (i < ws.size) {
          val cid = ws(i); i += 1
          if (conflict >= 0) { ws(j) = cid; j += 1 }
          else {
            val c = clauses(cid)
            if (c(0) == falseLit) { c(0) = c(1); c(1) = falseLit }
            if (valueOf(c(0)) == 1) { ws(j) = cid; j += 1 }
            else {
              var k = 2
              while (k < c.length && valueOf(c(k)) == -1) k += 1
              if (k < c.length) { c(1) = c(k); c(k) = falseLit; watches(idx(c(1))) += cid }
              else {
                ws(j) = cid; j += 1
                if (!enqueue(c(0), cid)) conflict = cid
              }
            }
          }
        }
        ws.remove(j, ws.size - j)
        if (conflict >= 0) return conflict
      }
      -1
    }

    private def analyze(confl: Int): (Array[Int], Int) = {
      val seen = new Array[Boolean](n + 1)
      val learnt = mutable.ArrayBuffer[Int](0)
      var counter = 0
      var p = 0
      var c = confl
      var ti = trail.size - 1
      var continue = true
      while (continue) {
        for (q <- clauses(c) if q != p) {
          val v = math.abs(q)
          if (!seen(v) && level(v) > 0) {
            seen(v) = true
            bumpVar(v)
            if (level(v) >= trailLim.size) counter += 1 else learnt += q
          }
        }
        while (!seen(math.abs(trail(ti)))) ti -= 1
        p = trail(ti); ti -= 1
        seen(math.abs(p)) = false
        counter -= 1
        if (counter == 0) continue = false else c = reason(math.abs(p))
      }
      learnt(0) = -p
      var back = 0
      if (learnt.size > 1) {
        var mi = 1
        for (k <- 2 until learnt.size) if (level(math.abs(learnt(k))) > level(math.abs(learnt(mi)))) mi = k
        val t = learnt(1); learnt(1) = learnt(mi); learnt(mi) = t
        back = level(math.abs(learnt(1)))
      }
      (learnt.toArray, back)
    }

    private def bumpVar(v: Int): Unit = {
      activity(v) += bump
      if (activity(v) > 1e100) { for (i <- 1 to n) activity(i) *= 1e-100; bump *= 1e-100 }
    }

    private def cancelUntil(lvl: Int): Unit =
      if (trailLim.size > lvl) {
        val keep = trailLim(lvl)
        var i = trail.size - 1
        while (i >= keep) {
          val v = math.abs(trail(i))
          phase(v) = trail(i) > 0
          value(v) = 0; reason(v) = -1
          i -= 1
        }
        trail.remove(keep, trail.size - keep)
        trailLim.remove(lvl, trailLim.size - lvl)
        qhead = keep
      }

    private def pickBranch(): Int = {
      var best = 0
      var ba = -1.0
      var v = 1
      while (v <= n) { if (value(v) == 0 && activity(v) > ba) { ba = activity(v); best = v }; v += 1 }
      if (best == 0) 0 else if (phase(best)) best else -best
    }

    def run(): Result = {
      // Normalize: drop tautologies and duplicate literals; units are enqueued at level 0.
      val units = mutable.ArrayBuffer.empty[Int]
      for (raw <- input) {
        val c = raw.distinct
        if (c.isEmpty) return Unsatisfiable(Seq(Array.empty[Int]))
        if (!c.exists(l => c.contains(-l))) {
          if (c.length == 1) units += c(0) else attach(c)
        }
      }
      for (u <- units) if (!enqueue(u, -1)) return Unsatisfiable(Seq(Array.empty[Int]))
      var conflicts = 0
      var restartAt = 100
      while (true) {
        val confl = propagate()
        if (confl >= 0) {
          conflicts += 1
          if (trailLim.isEmpty) { proof += Array.empty[Int]; return Unsatisfiable(proof.toSeq) }
          val (learnt, back) = analyze(confl)
          cancelUntil(back)
          proof += learnt
          if (learnt.length == 1) enqueue(learnt(0), -1)
          else { val id = attach(learnt); enqueue(learnt(0), id) }
          bump *= 1.05
        } else {
          if (conflicts >= restartAt) { restartAt += 100 + restartAt / 2; cancelUntil(0) }
          val next = pickBranch()
          if (next == 0) {
            val model = new Array[Boolean](n + 1)
            for (v <- 1 to n) model(v) = value(v) == 1
            return Satisfiable(model)
          }
          trailLim += trail.size
          enqueue(next, -1)
        }
      }
      throw new IllegalStateException("unreachable")
    }
  }
}

/**
 * Reverse unit propagation: a proof is a list of clauses, each of which follows from the formula
 * and the clauses before it by unit propagation alone; the last is empty. This checker shares no
 * code with [[Sat]], so a wrong `Unsat` is caught here.
 */
object Rup {
  def check(numVars: Int, clauses: Seq[Array[Int]], proof: Seq[Array[Int]]): Boolean = {
    val db = mutable.ArrayBuffer.from(clauses.map(_.distinct))
    proof.nonEmpty && proof.forall { c =>
      val ok = refutes(numVars, db, c)
      db += c
      ok
    } && proof.last.isEmpty
  }

  /** Assume every literal of `c` false and propagate; true when that reaches a conflict. */
  private def refutes(n: Int, db: scala.collection.Seq[Array[Int]], c: Array[Int]): Boolean = {
    val value = new Array[Int](n + 1)
    def set(l: Int): Boolean = { val v = math.abs(l); val want = if (l > 0) 1 else -1
      if (value(v) == -want) false else { value(v) = want; true } }
    for (l <- c) if (!set(-l)) return true
    var changed = true
    while (changed) {
      changed = false
      for (cl <- db) {
        var sat = false
        var unassigned = 0
        var last = 0
        for (l <- cl if !sat) {
          val v = value(math.abs(l))
          if (v == 0) { unassigned += 1; last = l }
          else if ((l > 0) == (v > 0)) sat = true
        }
        if (!sat) {
          if (unassigned == 0) return true
          if (unassigned == 1) { set(last); changed = true }
        }
      }
    }
    false
  }

}
