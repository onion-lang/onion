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
  private sealed trait Kind
  private case object Raise extends Kind
  private case object Lower extends Kind
  private case object SetConst extends Kind
  private case object Other extends Kind
  private final case class Write(field: String, kind: Kind, node: AST.Node)

  def run(units: Seq[AST.CompilationUnit]): Unit = {
    val classes: Map[String, AST.ClassDeclaration] =
      units.flatMap(_.toplevels).collect { case c: AST.ClassDeclaration => c.name -> c }.toMap
    val interfaces: Map[String, AST.InterfaceDeclaration] =
      units.flatMap(_.toplevels).collect { case i: AST.InterfaceDeclaration => i.name -> i }.toMap
    val unitOf: Map[String, AST.CompilationUnit] =
      units.flatMap(u => u.toplevels.collect { case c: AST.ClassDeclaration => c.name -> u }).toMap
    if (classes.values.exists(_.histories.nonEmpty) || interfaces.values.exists(_.histories.nonEmpty)) {
      for (cls <- classes.values) {
        typing.warningReporter_.setSourceFile(unitOf(cls.name).sourceFile)
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
        for (c <- chain; m <- methodsOf(c) if m.block != null) yield (m, writesIn(m.block))
      for {
        (m, writes) <- methodsOf(cls).filter(_.block != null).map(m => (m, writesIn(m.block)))
        shadowed = declaredNames(m)
        w <- writes if !shadowed.contains(w.field)
        (field, op) <- relations.distinct if field == w.field
        problem <- problemOf(op, w.kind, field, perMethod)
      } typing.report(SemanticError.HISTORY_BROKEN_BY_WRITE, w.node, field, s"$field $op old($field)", problem._1, problem._2)
      proveRest(cls, chain, relations.distinct)
    }
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
      m <- methodsOf(cls) if m.block != null && !writesIn(m.block).exists(w => relations.exists(_._1 == w.field) && brokenSyntactically(w, relations, chain))
      (field, op) <- relations
    } HistoryProver.prove(m, field, op, intFields, invariants) match {
      case HistoryProver.Counterexample(before) =>
        val shown = before.toSeq.sortBy(_._1).map { case (k, v) => s"$k = $v" }.mkString(", ")
        typing.warningReporter_.historyNotProved(m.location, m.name, s"$field $op old($field)", shown)
      case _ =>
    }
  }

  private def brokenSyntactically(w: Write, relations: Seq[(String, String)], chain: Seq[AST.ClassDeclaration]): Boolean = {
    val perMethod = for (c <- chain; m <- methodsOf(c) if m.block != null) yield (m, writesIn(m.block))
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

  /** Parameters and locals of a method: a bare name that is one of them is not the field. */
  private def declaredNames(m: AST.MethodDeclaration): Set[String] = {
    val names = Set.newBuilder[String]
    m.args.foreach(a => names += a.name)
    def visit(n: AST.Node): Unit = {
      n match {
        case l: AST.LocalVariableDeclaration => names += l.name
        case _ =>
      }
      ASTWalk.visitChildren(n)(visit)
    }
    visit(m.block)
    names.result()
  }

  private def writesIn(block: AST.Node): Seq[Write] = {
    val out = Seq.newBuilder[Write]
    def visit(n: AST.Node): Unit = {
      n match {
        case a: AST.Assignment => fieldOf(a.lhs).foreach(f => out += Write(f, assignKind(f, a.rhs), a))
        case a: AST.AdditionAssignment => fieldOf(a.lhs).foreach(f => out += Write(f, deltaKind(a.rhs, 1), a))
        case a: AST.SubtractionAssignment => fieldOf(a.lhs).foreach(f => out += Write(f, deltaKind(a.rhs, -1), a))
        case u: AST.PostIncrement => fieldOf(u.term).foreach(f => out += Write(f, Raise, u))
        case u: AST.PostDecrement => fieldOf(u.term).foreach(f => out += Write(f, Lower, u))
        case _ =>
      }
      ASTWalk.visitChildren(n)(visit)
    }
    visit(block)
    out.result()
  }

  private def assignKind(field: String, rhs: AST.Expression): Kind = rhs match {
    case AST.IntegerLiteral(_, _, _) => SetConst
    case AST.Addition(_, l, r) if fieldOf(l).contains(field) => deltaKind(r, 1)
    case AST.Subtraction(_, l, r) if fieldOf(l).contains(field) => deltaKind(r, -1)
    case _ => Other
  }

  /** `sign` is +1 for adding `e` and -1 for subtracting it. Only a constant is classified. */
  private def deltaKind(e: AST.Expression, sign: Int): Kind = e match {
    case AST.IntegerLiteral(_, k, _) =>
      val d = sign * k
      if (d > 0) Raise else if (d < 0) Lower else Other
    case _ => Other
  }

  private def fieldOf(e: AST.Expression): Option[String] = e match {
    case AST.Id(_, n) => Some(n)
    case AST.UnqualifiedFieldReference(_, n) => Some(n)
    case AST.MemberSelection(_, _: AST.CurrentInstance, n) => Some(n)
    case _ => None
  }
}
