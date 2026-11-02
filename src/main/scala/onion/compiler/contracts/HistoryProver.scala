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
  /**
   * `callees` are the methods of the class (and its supertypes) a body may call. A call is
   * modelled only for a callee that returns `Int`, has a body that reads and writes no field, and
   * states its contract: the result is a fresh word, the callee's `requires` must hold at the call
   * (an obligation, not an assumption), and its `ensures` constrains the result. Any other call is
   * [[Unsupported]].
   */
  /**
   * What the prover needs to know about a method it calls: its parameter names, and its contract.
   * It comes from the AST of a method in the same compilation ([[Callee.of]]) or from the
   * `@onion.Contract` of a class file built earlier (RFC #2557), which has no body to look at.
   */
  final case class Callee(params: List[String], requires: List[AST.Expression], ensures: List[AST.Expression])

  object Callee {
    /** A method of this compilation. Only an `Int` method over `Int` parameters that states a contract is a callee. */
    def of(m: AST.MethodDeclaration): Option[Callee] =
      if (m.args.forall(a => a.typeRef != null && a.typeRef.desc == AST.PrimitiveType(AST.KInt)) &&
          m.returnType != null && m.returnType.desc == AST.PrimitiveType(AST.KInt) && m.ensures.nonEmpty)
        Some(Callee(m.args.map(_.name), m.requires, m.ensures))
      else None
  }

  def prove(method: AST.MethodDeclaration, field: String, op: String, intFields: Set[String],
            invariants: Seq[AST.Expression], callees: Map[String, AST.MethodDeclaration] = Map.empty,
            external: Map[(String, String), Callee] = Map.empty): Outcome =
    if (method.block == null || !intFields.contains(field)) Unsupported
    else {
      val first = new Run(method, field, op, intFields, invariants, callees, external, checkObligations = true)
      first.run() match {
        // The callee obligations are a separate question, asked in a circuit of its own, so the
        // negated obligation does not stay in the clauses of the history question.
        case Proved => new Run(method, field, op, intFields, invariants, callees, external, checkObligations = false).run()
        case other  => other
      }
    }

  /**
   * Prove that `method`'s `ensures` hold, assuming `assume` (the effective precondition and the
   * class invariants) at entry. `ensures` may name `result` and the parameters; a clause the
   * encoder does not model makes the answer [[Unsupported]]. Used to check an override's body
   * against the effective contract (design 4).
   */
  def proveEnsures(method: AST.MethodDeclaration, ensures: Seq[AST.Expression], assume: Seq[AST.Expression],
                   intFields: Set[String], callees: Map[String, AST.MethodDeclaration] = Map.empty,
                   external: Map[(String, String), Callee] = Map.empty): Outcome =
    if (method.block == null || ensures.isEmpty) Unsupported
    else new Run(method, "", ">=", intFields, assume, callees, external, checkObligations = false).runEnsures(ensures)

  private final class Run(method: AST.MethodDeclaration, field: String, op: String, intFields: Set[String],
                          invariants: Seq[AST.Expression], callees: Map[String, AST.MethodDeclaration],
                          external: Map[(String, String), Callee], checkObligations: Boolean) {
    /** Conditions the solver may assume (a callee's `ensures`) and must establish (its `requires`). */
    private val assumed = scala.collection.mutable.ArrayBuffer.empty[Int]
    private val obligations = scala.collection.mutable.ArrayBuffer.empty[Int]
    private var unsupportedCall = false
    /** The value of a trailing `return e`, once seen: what an `ensures` calls `result`. */
    private var returned: Option[bb.Word] = None
    private val bb = new BitBlast
    private type Env = Map[String, bb.Word]

    def run(): Outcome = {
      val intParams = method.args.filter(a => isInt(a.typeRef)).map(_.name)
      // A non-Int parameter may be read, so it is refused, unless it is the receiver of a call whose
      // contract a built module gave us: it is not in the environment, so reading it as a value
      // still ends in Unsupported.
      if (method.args.exists(a => !isInt(a.typeRef) && !isBuiltReceiver(a))) return Unsupported
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
      assumed.foreach(bb.assertTrue)
      if (checkObligations) {
        // Can a callee's `requires` fail at a call, given the invariants, the caller's `requires`
        // and the callees' `ensures`? If so the call is not understood: Unsupported, never a guess.
        if (obligations.isEmpty) return Proved
        bb.assertTrue(obligations.map(o => -o).reduceLeft(bb.or))
        return bb.solve() match {
          case Sat.Unsatisfiable(proof) => if (Rup.check(bb.numVars, bb.allClauses, proof)) Proved else Unsupported
          case _                        => Unsupported
        }
      }
      bb.assertTrue(-holds)
      bb.solve() match {
        case Sat.Unsatisfiable(proof) => if (Rup.check(bb.numVars, bb.allClauses, proof)) Proved else Unsupported
        case Sat.Satisfiable(model) =>
          Counterexample(pre.view.mapValues(w => bb.valueOf(model, w).toInt).toMap)
      }
    }

    /** The body is run symbolically; `result` is the value of its trailing `return`. */
    def runEnsures(ensures: Seq[AST.Expression]): Outcome = {
      val intParams = method.args.filter(a => isInt(a.typeRef)).map(_.name)
      if (method.args.size != intParams.size) return Unsupported
      val pre: Env = (intFields.toSeq ++ intParams).map(n => n -> bb.fresh(W)).toMap
      val assumptions = (invariants ++ method.requires).map(cond(_, pre))
      if (assumptions.exists(_.isEmpty)) return Unsupported
      val post = exec(method.block.elements, pre).getOrElse(return Unsupported)
      val value = returned.getOrElse(return Unsupported)
      // `old(e)` is e at entry, so it is read from `pre`; a bare name is read from `post`.
      val goals = ensures.map(e => condPost(e, pre, post + ("result" -> value)))
      if (goals.exists(_.isEmpty)) return Unsupported
      assumptions.flatten.foreach(bb.assertTrue)
      assumed.foreach(bb.assertTrue)
      bb.assertTrue(goals.flatten.map(g => -g).reduceLeft(bb.or))
      bb.solve() match {
        case Sat.Unsatisfiable(proof) => if (Rup.check(bb.numVars, bb.allClauses, proof)) Proved else Unsupported
        case Sat.Satisfiable(model) =>
          Counterexample(pre.view.mapValues(w => bb.valueOf(model, w).toInt).toMap)
      }
    }

    /** A post-state condition: `old(e)` reads `pre`, everything else reads `post`. */
    private def condPost(e: AST.Expression, pre: Env, post: Env): Option[Int] = e match {
      case AST.LogicalAnd(_, l, r) => for (a <- condPost(l, pre, post); b <- condPost(r, pre, post)) yield bb.and(a, b)
      case AST.LogicalOr(_, l, r)  => for (a <- condPost(l, pre, post); b <- condPost(r, pre, post)) yield bb.or(a, b)
      case AST.Not(_, t)           => condPost(t, pre, post).map(-_)
      // `old(c)` of a whole condition: c read entirely at entry
      case AST.UnqualifiedMethodCall(_, "old", List(inner), _) => cond(inner, pre)
      case c: AST.BinaryExpression if isCmp(c) =>
        for (a <- exprPost(c.lhs, pre, post); b <- exprPost(c.rhs, pre, post)) yield compare(c, a, b)
      case _ => None
    }

    private def isCmp(c: AST.BinaryExpression): Boolean = c match {
      case _: AST.LessThan | _: AST.GreaterThan | _: AST.LessOrEqual | _: AST.GreaterOrEqual |
           _: AST.Equal | _: AST.NotEqual => true
      case _ => false
    }

    private def compare(c: AST.BinaryExpression, a: bb.Word, b: bb.Word): Int = c match {
      case _: AST.LessThan       => bb.slt(a, b)
      case _: AST.GreaterThan    => bb.slt(b, a)
      case _: AST.LessOrEqual    => -bb.slt(b, a)
      case _: AST.GreaterOrEqual => -bb.slt(a, b)
      case _: AST.Equal          => bb.eq(a, b)
      case _                     => -bb.eq(a, b)
    }

    private def exprPost(e: AST.Expression, pre: Env, post: Env): Option[bb.Word] = e match {
      case AST.UnqualifiedMethodCall(_, "old", List(inner), _) => expr(inner, pre)
      case AST.Addition(_, l, r)       => for (a <- exprPost(l, pre, post); b <- exprPost(r, pre, post)) yield bb.add(a, b)
      case AST.Subtraction(_, l, r)    => for (a <- exprPost(l, pre, post); b <- exprPost(r, pre, post)) yield bb.sub(a, b)
      case AST.Multiplication(_, l, r) => for (a <- exprPost(l, pre, post); b <- exprPost(r, pre, post)) yield bb.mul(a, b)
      case AST.Negate(_, t)            => exprPost(t, pre, post).map(bb.neg)
      case other                       => expr(other, post)
    }
    /** A parameter of a type some built module gave a contract for: only its methods are ever called. */
    private def isBuiltReceiver(a: AST.Argument): Boolean =
      Option(a.typeRef).map(_.desc).collect { case AST.ReferenceType(n, _) => n }.exists(n => external.keys.exists(_._1 == n))

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
        if (result == null) Some(env) else expr(result, env).map { w => returned = Some(w); env }
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
      case AST.UnqualifiedMethodCall(_, callee, args, _) => call(callee, args, env)
      case AST.MethodCall(_, AST.Id(_, receiver), callee, args, _) => memberCall(receiver, callee, args, env)
      case other => name(other).flatMap(env.get)
    }

    /**
     * A call to a pure, contract-bearing `Int` method of the class. The result is a fresh word;
     * the callee's `requires` become obligations and its `ensures` become assumptions, both over
     * the actual arguments. Nothing else about the callee is used, so nothing is guessed.
     */
    private def call(name: String, args: List[AST.Expression], env: Env): Option[bb.Word] =
      for {
        m <- callees.get(name)
        if m.args.size == args.size && isInt(m.returnType) && m.args.forall(a => isInt(a.typeRef))
        if m.ensures.nonEmpty && readsNoField(m)
        actuals <- sequence(args.map(expr(_, env)))
      } yield {
        val result = bb.fresh(W)
        // the callee's own scope: its parameters bound to the actuals, `result` to the fresh word
        val scope: Env = m.args.map(_.name).zip(actuals).toMap + ("result" -> result)
        m.requires.foreach(r => cond(r, scope).foreach(obligations += _))
        m.ensures.foreach(e => cond(e, scope).foreach(assumed += _))
        result
      }

    /**
     * `s.step(k)` where `s` is a parameter whose declared type has a contract from a module built
     * earlier. The receiver is another object, so the call cannot touch this object's fields, unless
     * `s` is of this very class (it could then be this object): that case is not modelled.
     */
    private def memberCall(receiver: String, name: String, args: List[AST.Expression], env: Env): Option[bb.Word] =
      for {
        param <- method.args.find(_.name == receiver)
        typeName <- Option(param.typeRef).map(_.desc).collect { case AST.ReferenceType(n, _) => n }
        c <- external.get((typeName, name))
        if c.params.size == args.size
        actuals <- sequence(args.map(expr(_, env)))
      } yield {
        val result = bb.fresh(W)
        val scope: Env = c.params.zip(actuals).toMap + ("result" -> result)
        c.requires.foreach(r => cond(r, scope).foreach(obligations += _))
        c.ensures.foreach(e => cond(e, scope).foreach(assumed += _))
        result
      }

    private def sequence[A](xs: List[Option[A]]): Option[List[A]] =
      if (xs.forall(_.isDefined)) Some(xs.map(_.get)) else None

    /** The callee's body must not touch the object: no field is read or written. */
    private def readsNoField(m: AST.MethodDeclaration): Boolean =
      m.block != null && !intFields.exists(f => mentions(m.block, f)) && !m.args.exists(_ => false)

    private def mentions(n: AST.Node, field: String): Boolean = {
      var found = false
      def visit(x: AST.Node): Unit = {
        x match {
          case AST.Id(_, `field`) | AST.UnqualifiedFieldReference(_, `field`) => found = true
          case AST.MemberSelection(_, _: AST.CurrentInstance, `field`) => found = true
          case _ =>
        }
        if (!found) onion.compiler.ASTWalk.visitChildren(x)(visit)
      }
      visit(n)
      found
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
