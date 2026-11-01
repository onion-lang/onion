package onion.compiler.contracts

import onion.compiler.AST
import onion.compiler.contracts.smt.{BitBlast, Rup, Sat}

/**
 * Proves a `history` for one method by symbolic execution and the bit-vector solver (RFC #2557,
 * design 7.3). The method body is run on symbolic 32-bit words; the question put to the solver is
 * "can the invariants and the `requires` hold before, and the history fail after?".
 *
 *  - `unsat`: [[Proved]], and the proof replays in [[Rup]]. Proofs that fail to replay are
 *    reported as [[Unsupported]], never as proved.
 *  - `sat`: [[Counterexample]] with the values before the call. Int arithmetic wraps like the
 *    JVM, so `n = n + 1` against `history n >= old(n)` has the counterexample `n = 2147483647`
 *    unless an `invariant` or `requires` rules it out.
 *  - anything the encoder does not understand (calls, loops, `return`, locals, non-Int fields):
 *    [[Unsupported]]. It never guesses, because a guess would be a false error.
 */
object HistoryProver {
  sealed trait Outcome
  case object Proved extends Outcome
  final case class Counterexample(before: Map[String, Int]) extends Outcome
  case object Unsupported extends Outcome

  private val W = 32

  /** Prove `field op old(field)` (`op` is `>=`, `<=` or `==`) for `method`, assuming the class `invariants`. */
  def prove(method: AST.MethodDeclaration, field: String, op: String, intFields: Set[String],
            invariants: Seq[AST.Expression]): Outcome =
    if (method.block == null || !intFields.contains(field)) Unsupported
    else new Run(method, field, op, intFields, invariants).run()

  private final class Run(method: AST.MethodDeclaration, field: String, op: String, intFields: Set[String],
                          invariants: Seq[AST.Expression]) {
    private val bb = new BitBlast
    private type Env = Map[String, bb.Word]

    def run(): Outcome = {
      val intParams = method.args.filter(a => isInt(a.typeRef)).map(_.name)
      if (method.args.size != intParams.size) return Unsupported // a non-Int parameter may be read
      val pre: Env = (intFields.toSeq ++ intParams).map(n => n -> bb.fresh(W)).toMap
      val assumptions = (invariants ++ method.requires).map(cond(_, pre))
      if (assumptions.exists(_.isEmpty)) return Unsupported
      val post = exec(method.block.elements, pre).getOrElse(return Unsupported)
      val (before, after) = (pre(field), post(field))
      val holds = op match {
        case ">=" => -bb.slt(after, before)
        case "<=" => -bb.slt(before, after)
        case _    => bb.eq(after, before)
      }
      assumptions.flatten.foreach(bb.assertTrue)
      bb.assertTrue(-holds)
      bb.solve() match {
        case Sat.Unsatisfiable(proof) => if (Rup.check(bb.numVars, bb.allClauses, proof)) Proved else Unsupported
        case Sat.Satisfiable(model) =>
          Counterexample(pre.view.mapValues(w => bb.valueOf(model, w).toInt).toMap)
      }
    }

    private def isInt(t: AST.TypeNode): Boolean = t != null && (t.desc match {
      case AST.PrimitiveType(AST.KInt) => true
      case _ => false
    })

    // ---- statements ------------------------------------------------------------------------
    private def exec(elements: List[AST.BlockElement], env: Env): Option[Env] =
      elements.zipWithIndex.foldLeft(Option(env)) { case (acc, (el, i)) =>
        acc.flatMap(e => step(el, e, last = i == elements.size - 1))
      }

    private def step(el: AST.BlockElement, env: Env, last: Boolean): Option[Env] = el match {
      // A local of Int type: a fresh name bound to the symbolic value. A name that is already a
      // field or parameter would shadow it, which the encoding does not track, so it is refused.
      case AST.LocalVariableDeclaration(_, _, name, typeRef, init) if init != null && (typeRef == null || isInt(typeRef)) =>
        if (env.contains(name)) None else expr(init, env).map(w => env.updated(name, w))
      // A `return` as the last statement ends the body; the value must be one the encoder models.
      case AST.ReturnExpression(_, result) if last =>
        if (result == null) Some(env) else expr(result, env).map(_ => env)
      case AST.Assignment(_, lhs, rhs) => name(lhs).filter(env.contains).flatMap(n => expr(rhs, env).map(w => env.updated(n, w)))
      case AST.AdditionAssignment(_, lhs, rhs) => update(lhs, env, (a, b) => bb.add(a, b), rhs)
      case AST.SubtractionAssignment(_, lhs, rhs) => update(lhs, env, (a, b) => bb.sub(a, b), rhs)
      case AST.PostIncrement(_, t) => update(t, env, (a, b) => bb.add(a, b), AST.IntegerLiteral(null, 1))
      case AST.PostDecrement(_, t) => update(t, env, (a, b) => bb.sub(a, b), AST.IntegerLiteral(null, 1))
      case AST.IfExpression(_, c, thenB, elseB) =>
        for {
          cl <- cond(c, env)
          t <- exec(thenB.elements, env)
          e <- if (elseB == null) Some(env) else exec(elseB.elements, env)
        } yield env.keys.map(k => k -> bb.mux(cl, t(k), e(k))).toMap
      case _ => None
    }

    private def update(lhs: AST.Expression, env: Env, f: (bb.Word, bb.Word) => bb.Word, rhs: AST.Expression): Option[Env] =
      for { n <- name(lhs).filter(env.contains); r <- expr(rhs, env) } yield env.updated(n, f(env(n), r))

    // ---- expressions -----------------------------------------------------------------------
    private def name(e: AST.Expression): Option[String] = e match {
      case AST.Id(_, n) => Some(n)
      case AST.UnqualifiedFieldReference(_, n) => Some(n)
      case AST.MemberSelection(_, _: AST.CurrentInstance, n) => Some(n)
      case _ => None
    }

    private def expr(e: AST.Expression, env: Env): Option[bb.Word] = e match {
      case AST.IntegerLiteral(_, v, _) => Some(bb.const(W, v.toLong))
      case AST.Addition(_, l, r) => bin(l, r, env)(bb.add)
      case AST.Subtraction(_, l, r) => bin(l, r, env)(bb.sub)
      case AST.Multiplication(_, l, r) => bin(l, r, env)(bb.mul)
      case AST.Negate(_, t) => expr(t, env).map(bb.neg)
      case other => name(other).flatMap(env.get)
    }

    private def bin(l: AST.Expression, r: AST.Expression, env: Env)(f: (bb.Word, bb.Word) => bb.Word): Option[bb.Word] =
      for (a <- expr(l, env); b <- expr(r, env)) yield f(a, b)

    private def cond(e: AST.Expression, env: Env): Option[Int] = e match {
      case AST.BooleanLiteral(_, v) => Some(if (v) bb.tru else bb.fls)
      case AST.LessThan(_, l, r) => cmp(l, r, env)((a, b) => bb.slt(a, b))
      case AST.GreaterThan(_, l, r) => cmp(l, r, env)((a, b) => bb.slt(b, a))
      case AST.LessOrEqual(_, l, r) => cmp(l, r, env)((a, b) => -bb.slt(b, a))
      case AST.GreaterOrEqual(_, l, r) => cmp(l, r, env)((a, b) => -bb.slt(a, b))
      case AST.Equal(_, l, r) => cmp(l, r, env)((a, b) => bb.eq(a, b))
      case AST.NotEqual(_, l, r) => cmp(l, r, env)((a, b) => -bb.eq(a, b))
      case AST.LogicalAnd(_, l, r) => for (a <- cond(l, env); b <- cond(r, env)) yield bb.and(a, b)
      case AST.LogicalOr(_, l, r) => for (a <- cond(l, env); b <- cond(r, env)) yield bb.or(a, b)
      case AST.Not(_, t) => cond(t, env).map(-_)
      case _ => None
    }

    private def cmp(l: AST.Expression, r: AST.Expression, env: Env)(f: (bb.Word, bb.Word) => Int): Option[Int] =
      for (a <- expr(l, env); b <- expr(r, env)) yield f(a, b)
  }
}
