package onion.compiler.typing

import onion.compiler.{AST, ASTWalk, SemanticError, Typing}
import onion.compiler.contracts.HistoryProver

/**
 * Pass 7 of typing (RFC #2557, design 4.1): a write that can break a `history`.
 *
 * Take `history n >= old(n)`. A method that lowers `n` breaks it for every `n`. A method that
 * sets `n` to a constant breaks it only if `n` can be above that constant, and the syntax says so
 * when some method of the class or a supertype raises `n`. A class whose only write is `n = 0`
 * is fine, since `n` never rises above its start. Anything the syntax does not settle is left to
 * the prover (a later stage) and is not reported.
 *
 * This is how `ResettableCounter.reset` is caught: it sets `n` to 0, and `Counter.inc` raises `n`.
 * Constructors are not checked: a history relates the states around a call, and a constructor
 * has no earlier state.
 */
final class HistoryWritePass(typing: Typing) {
  import HistoryWritePass.{Repro, Unchecked}
  // `counterexamples` runs the same analysis without reporting and collects what the solver found.
  private var collected: scala.collection.mutable.ArrayBuffer[Repro] = null
  /** Histories the solver proved for a method, as `Class.method: field >= old(field)`. */
  val proved: scala.collection.mutable.ArrayBuffer[String] = scala.collection.mutable.ArrayBuffer.empty
  /** Writes that neither the syntax nor the solver settled (W0024): never left silent (#2574). */
  val unchecked: scala.collection.mutable.ArrayBuffer[Unchecked] = scala.collection.mutable.ArrayBuffer.empty
  private var unitFile: String = null
  private sealed trait Kind
  private case object Raise extends Kind
  private case object Lower extends Kind
  private case object SetConst extends Kind
  private case object Other extends Kind
  /**
   * A write to `field`. `bare` is a write through the plain name, which a parameter or local of
   * the same name would shadow; `this.t` and `o.t` are not bare.
   */
  private final case class Write(field: String, kind: Kind, node: AST.Node, bare: Boolean, inLambda: Boolean = false) {
    def ofField(m: AST.MethodDeclaration, f: String): Boolean = field == f && !(bare && declaredNames(m).contains(f))
  }
  /**
   * A write of this object's field: `bare` is the plain name, `this.t` and `self.t` are not bare.
   * A write through any other receiver is not followed here: it is E0110 (design 3.2, rule 2;
   * [[ContractFramingPass]]), whatever its receiver's class.
   */
  private final case class Slot(field: String, bare: Boolean)

  /** The solver's counterexamples, for the phase that tries to reproduce them by running the code. */
  def counterexamples(units: Seq[AST.CompilationUnit]): Seq[Repro] = {
    collected = scala.collection.mutable.ArrayBuffer.empty
    try analyze(units, report = false) finally ()
    collected.toSeq
  }

  def run(units: Seq[AST.CompilationUnit]): Unit = analyze(units, report = true)

  private var reporting = true

  private def analyze(units: Seq[AST.CompilationUnit], report: Boolean): Unit = {
    reporting = report
    val classes: Map[String, AST.ClassDeclaration] =
      units.flatMap(_.toplevels).collect { case c: AST.ClassDeclaration => c.name -> c }.toMap
    val interfaces: Map[String, AST.InterfaceDeclaration] =
      units.flatMap(_.toplevels).collect { case i: AST.InterfaceDeclaration => i.name -> i }.toMap
    val unitOf: Map[String, AST.CompilationUnit] =
      units.flatMap(u => u.toplevels.collect { case c: AST.ClassDeclaration => c.name -> u }).toMap
    if (classes.values.exists(_.histories.nonEmpty) || interfaces.values.exists(_.histories.nonEmpty)) {
      for (cls <- classes.values) {
        unitFile = unitOf(cls.name).sourceFile
        check(cls, classes, interfaces)
      }
    }
  }

  private def check(cls: AST.ClassDeclaration, classes: Map[String, AST.ClassDeclaration],
                     interfaces: Map[String, AST.InterfaceDeclaration]): Unit = {
    val chain = hierarchy(cls, classes)
    // An interface's history binds every implementor (design 4.2), like a superclass's.
    val histories = chain.flatMap(_.histories) ++ interfaceHistories(chain.flatMap(_.superInterfaces), interfaces, Set.empty)
    val relations = for {
      h <- histories
      (field, op) <- HistoryPreorder.simpleRelations(h)
    } yield (field, op)
    if (relations.nonEmpty) {
      val perMethod: Seq[(AST.MethodDeclaration, Seq[Write])] =
        for (c <- chain; m <- methodsOf(c) if m.block != null) yield (m, writesIn(m, c))
      for {
        (m, writes) <- methodsOf(cls).filter(_.block != null).map(m => (m, writesIn(m, cls)))
        (field, op) <- relations.distinct
        w <- writes if w.ofField(m, field)
        problem <- problemOf(op, w.kind, field, perMethod)
      } if (reporting) typing.report(SemanticError.HISTORY_BROKEN_BY_WRITE, w.node, field, s"$field $op old($field)", problem._1, problem._2)
      if (!reporting) proveRest(cls, chain, relations.distinct)
    }
    if (!reporting) uncheckedForms(cls, chain, histories)
  }

  /**
   * A history (or a conjunct of one) that is not `x >= old(x)`, `x <= old(x)` or `x == old(x)` is
   * checked by nothing yet. Each method that writes a field it names is told so, with its line.
   */
  private def uncheckedForms(cls: AST.ClassDeclaration, chain: Seq[AST.ClassDeclaration], histories: Seq[AST.Expression]): Unit = {
    val fields = fieldTypes(chain).keySet
    for {
      h <- histories
      c <- conjuncts(h) if HistoryPreorder.simpleRelations(c).isEmpty
      named = namesIn(c).filter(fields.contains)
      m <- methodsOf(cls) if m.block != null
      w <- writesIn(m, cls).find(w => named.exists(w.ofField(m, _)))
    } unchecked += Unchecked(unitFile, cls, m, w.field, None, line = Option(h.location).map(_.line).getOrElse(0), fieldType = None)
  }

  private def conjuncts(e: AST.Expression): Seq[AST.Expression] = e match {
    case AST.LogicalAnd(_, l, r) => conjuncts(l) ++ conjuncts(r)
    case other                   => Seq(other)
  }

  private def namesIn(e: AST.Node): Set[String] = {
    val names = Set.newBuilder[String]
    def visit(n: AST.Node): Unit = {
      n match {
        case e: AST.Expression => fieldOf(e).foreach(names += _)
        case _ =>
      }
      ASTWalk.visitChildren(n)(visit)
    }
    visit(e)
    names.result()
  }

  /** The fields declared in the class and its superclasses, with how each type is spelled. */
  private def fieldTypes(chain: Seq[AST.ClassDeclaration]): Map[String, String] =
    chain.reverse.flatMap(c => (c.defaultSection.toSeq ++ c.sections).flatMap(_.members)).collect {
      case f: AST.FieldDeclaration => f.name -> typeName(f.typeRef)
    }.toMap

  private def typeName(t: AST.TypeNode): String = if (t == null) "" else t.desc match {
    case AST.PrimitiveType(k)    => k.name.capitalize
    case AST.ReferenceType(n, _) => n.split('.').last
    case other                   => other.toString
  }

  /**
   * What the syntax did not settle goes to the solver (design 7.3). A counterexample is a warning
   * (W0021), not an error: it may rest on a wrap-around the program never reaches, and only a
   * run can confirm it. A method with a syntactic E0109 is skipped; it is already reported.
   */
  private def proveRest(cls: AST.ClassDeclaration, chain: Seq[AST.ClassDeclaration], relations: Seq[(String, String)]): Unit = {
    val intFields = chain.flatMap(c => (c.defaultSection.toSeq ++ c.sections).flatMap(_.members)).collect {
      case f: AST.FieldDeclaration if f.typeRef != null && f.typeRef.desc == AST.PrimitiveType(AST.KInt) => f.name
    }.toSet
    val invariants = chain.flatMap(_.invariants)
    for {
      m <- methodsOf(cls) if m.block != null && !writesIn(m, cls).exists(w => relations.exists(r => w.ofField(m, r._1)) && brokenSyntactically(w, relations, chain))
      (field, op) <- relations
    } hiddenWrite(writesIn(m, cls).filter(_.ofField(m, field))) match {
      // A write in a lambda, which the body-only checks cannot follow, is said as such at its line (#2599).
      case Some(w) =>
        unchecked += Unchecked(unitFile, cls, m, field, Some(s"$field $op old($field)"), line = lineOf(w.node),
          fieldType = None, reason = Some(("lambda", "")), at = w.node.location)
      case None => HistoryProver.prove(m, field, op, intFields, invariants) match {
      case HistoryProver.Counterexample(before) =>
        collected += Repro(unitFile, cls, m, field, op, before)
      case HistoryProver.Proved => proved += s"${cls.name}.${m.name}: $field $op old($field)"
      case HistoryProver.Unsupported if writesIn(m, cls).exists(_.ofField(m, field)) =>
        val ty = fieldTypes(chain).get(field)
        unchecked += Unchecked(unitFile, cls, m, field, Some(s"$field $op old($field)"), line = 0,
          fieldType = ty.filter(_ != "Int"))
      case _ =>
    }
    }
  }

  /** A write the solver cannot see: one inside a lambda. */
  private def hiddenWrite(ws: Seq[Write]): Option[Write] = ws.find(_.inLambda)

  private def lineOf(n: AST.Node): Int = Option(n.location).map(_.line).getOrElse(0)

  private def brokenSyntactically(w: Write, relations: Seq[(String, String)], chain: Seq[AST.ClassDeclaration]): Boolean = {
    val perMethod = for (c <- chain; m <- methodsOf(c) if m.block != null) yield (m, writesIn(m, c))
    relations.exists { case (f, op) => f == w.field && problemOf(op, w.kind, f, perMethod).isDefined }
  }

  /** `(reason kind, name of the method that makes the write wrong)`, or none when the syntax does not settle it. */
  private def problemOf(op: String, kind: Kind, field: String,
                        methods: Seq[(AST.MethodDeclaration, Seq[Write])]): Option[(String, String)] = {
    def rewriter(wanted: Kind): Option[String] =
      methods.collectFirst { case (m, ws) if ws.exists(w => w.field == field && w.kind == wanted) => m.name }
    (op, kind) match {
      case (">=", Lower)    => Some(("lower", ""))
      case (">=", SetConst) => rewriter(Raise).map(n => ("set", n))
      case ("<=", Raise)    => Some(("raise", ""))
      case ("<=", SetConst) => rewriter(Lower).map(n => ("setdown", n))
      case ("==", _)        => Some(("any", ""))
      case _                => None
    }
  }

  private def interfaceHistories(refs: Seq[AST.TypeNode], interfaces: Map[String, AST.InterfaceDeclaration],
                                 seen: Set[String]): Seq[AST.Expression] =
    refs.map(_.desc).collect { case AST.ReferenceType(n, _) => n }.filterNot(seen.contains).flatMap { n =>
      interfaces.get(n).toSeq.flatMap(i => i.histories ++ interfaceHistories(i.superInterfaces, interfaces, seen + n))
    }

  /** The class and its superclasses declared in the program, the class first. */
  private def hierarchy(cls: AST.ClassDeclaration, classes: Map[String, AST.ClassDeclaration]): Seq[AST.ClassDeclaration] = {
    @annotation.tailrec
    def go(c: AST.ClassDeclaration, seen: Seq[AST.ClassDeclaration]): Seq[AST.ClassDeclaration] = {
      val next = Option(c.superClass).map(_.desc).collect { case AST.ReferenceType(n, _) => n }
        .flatMap(classes.get).filterNot(seen.contains)
      next match {
        case Some(parent) => go(parent, seen :+ parent)
        case None         => seen
      }
    }
    go(cls, Seq(cls))
  }

  private def methodsOf(c: AST.ClassDeclaration): Seq[AST.MethodDeclaration] =
    (c.defaultSection.toSeq ++ c.sections).flatMap(_.members).collect { case m: AST.MethodDeclaration => m }

  /**
   * Parameters and locals of a method: a bare name that is one of them is not the field. A
   * lambda's own names shadow only inside the lambda, which [[writesIn]] handles (#2599).
   */
  private def declaredNames(m: AST.MethodDeclaration): Set[String] =
    m.args.map(_.name).toSet ++ localsIn(m.block)

  /**
   * Every write `m` makes to a field of this object (#2597): in its body, and inside the lambdas
   * it creates, since a lambda may run during the call.
   */
  private def writesIn(m: AST.MethodDeclaration, owner: AST.ClassDeclaration): Seq[Write] = {
    val out = Seq.newBuilder[Write]
    def visit(n: AST.Node, hidden: Set[String], inLambda: Boolean): Unit = {
      def add(lhs: AST.Expression, node: AST.Node, kind: Slot => Kind): Unit =
        slotOf(lhs, hidden).foreach(s => out += Write(s.field, kind(s), node, s.bare, inLambda))
      n match {
        case c: AST.ClosureExpression =>
          // Its own parameters and locals shadow a field of the same name inside it.
          if (c.body != null) visit(c.body, hidden ++ c.args.map(_.name) ++ localsIn(c.body), inLambda = true)
        case a: AST.Assignment => add(a.lhs, a, s => assignKind(s, a.rhs, hidden))
        case a: AST.AdditionAssignment => add(a.lhs, a, _ => deltaKind(a.rhs, 1))
        case a: AST.SubtractionAssignment => add(a.lhs, a, _ => deltaKind(a.rhs, -1))
        case u: AST.PostIncrement => add(u.term, u, _ => Raise)
        case u: AST.PostDecrement => add(u.term, u, _ => Lower)
        case _ =>
      }
      if (!n.isInstanceOf[AST.ClosureExpression]) ASTWalk.visitChildren(n)(visit(_, hidden, inLambda))
    }
    visit(m.block, Set.empty, inLambda = false)
    out.result()
  }

  /** The locals `n` declares, not counting those of the lambdas inside it. */
  private def localsIn(n: AST.Node): Set[String] = localDecls(n).map(_.name).toSet

  private def localDecls(n: AST.Node): Seq[AST.LocalVariableDeclaration] = {
    val out = Seq.newBuilder[AST.LocalVariableDeclaration]
    def visit(x: AST.Node): Unit = x match {
      case _: AST.ClosureExpression => ()
      case l: AST.LocalVariableDeclaration => out += l; ASTWalk.visitChildren(x)(visit)
      case _ => ASTWalk.visitChildren(x)(visit)
    }
    if (n != null) ASTWalk.visitChildren(n)(visit)
    out.result()
  }

  private def slotOf(e: AST.Expression, hidden: Set[String]): Option[Slot] = e match {
    case AST.Id(_, n) if !hidden.contains(n) => Some(Slot(n, bare = true))
    case AST.UnqualifiedFieldReference(_, n) if !hidden.contains(n) => Some(Slot(n, bare = true))
    case AST.MemberSelection(_, _: AST.CurrentInstance, n) => Some(Slot(n, bare = false))
    case _ => None
  }

  private def assignKind(slot: Slot, rhs: AST.Expression, hidden: Set[String]): Kind = {
    def same(e: AST.Expression) = slotOf(e, hidden).exists(_.field == slot.field)
    rhs match {
      case e if constant(e).isDefined => SetConst
      case AST.Addition(_, l, r) if same(l) => deltaKind(r, 1)
      case AST.Subtraction(_, l, r) if same(l) => deltaKind(r, -1)
      case _ => Other
    }
  }

  /** `sign` is +1 for adding `e` and -1 for subtracting it. Only a constant is classified. */
  private def deltaKind(e: AST.Expression, sign: Int): Kind = constant(e) match {
    case Some(k) =>
      val d = sign * k
      if (d > 0) Raise else if (d < 0) Lower else Other
    case None => Other
  }

  /** A numeric literal's value, whatever its width: `1`, `1L`, `1.5`, `1.5f`, `1S`, `1B` (#2574). */
  private def constant(e: AST.Expression): Option[Double] = e match {
    case AST.IntegerLiteral(_, k, _) => Some(k.toDouble)
    case AST.LongLiteral(_, k)       => Some(k.toDouble)
    case AST.DoubleLiteral(_, k) if !k.isNaN => Some(k)
    case AST.FloatLiteral(_, k) if !k.isNaN  => Some(k.toDouble)
    case AST.ShortLiteral(_, k)      => Some(k.toDouble)
    case AST.ByteLiteral(_, k)       => Some(k.toDouble)
    case _                           => None
  }

  private def fieldOf(e: AST.Expression): Option[String] = e match {
    case AST.Id(_, n) => Some(n)
    case AST.UnqualifiedFieldReference(_, n) => Some(n)
    case AST.MemberSelection(_, _: AST.CurrentInstance, n) => Some(n)
    case _ => None
  }
}

object HistoryWritePass {
  /** A counterexample the solver found: the method, the history, and the values before the call. */
  final case class Repro(sourceFile: String, cls: AST.ClassDeclaration, method: AST.MethodDeclaration,
                         field: String, op: String, before: Map[String, Int]) {
    def history: String = s"$field $op old($field)"
    def shown: String = before.toSeq.sortBy(_._1).map { case (k, v) => s"$k = $v" }.mkString(", ")
  }

  /**
   * A write to `field` in `method` that no check settled. `history` is the simple relation the
   * solver could not decide, or none for a history of another form (then `line` names it).
   * `fieldType` is set when the field is not an `Int`, which the solver does not model. `reason` is
   * a write the body-only checks cannot follow (`lambda`), reported `at` the write rather than at
   * the method (#2599).
   */
  final case class Unchecked(sourceFile: String, cls: AST.ClassDeclaration, method: AST.MethodDeclaration,
                             field: String, history: Option[String], line: Int, fieldType: Option[String],
                             reason: Option[(String, String)] = None, at: onion.compiler.Location = null)
}
