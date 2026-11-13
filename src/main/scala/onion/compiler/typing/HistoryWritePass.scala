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
final class HistoryWritePass(typing: Typing, builtType: String => Option[onion.compiler.TypedAST.ClassType] = _ => None,
                             typedOf: AST.Node => Option[onion.compiler.TypedAST.Node] = _ => None) {
  import HistoryWritePass.{Repro, Unchecked}
  // `counterexamples` runs the same analysis without reporting and collects what the solver found.
  private var collected: scala.collection.mutable.ArrayBuffer[Repro] = null
  /** Histories the solver proved for a method, as `Class.method: field >= old(field)`. */
  val proved: scala.collection.mutable.ArrayBuffer[String] = scala.collection.mutable.ArrayBuffer.empty
  /** Writes that neither the syntax nor the solver settled (W0024): never left silent (#2574). */
  val unchecked: scala.collection.mutable.ArrayBuffer[Unchecked] = scala.collection.mutable.ArrayBuffer.empty
  private var unitFile: String = null
  private var unitOf: Map[String, AST.CompilationUnit] = Map.empty
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

  /** Under `--contracts runtime` the compiler keeps a guard only for what it could not prove. */
  private def dropsProvedGuards: Boolean =
    typing != null && typing.contractMode == onion.compiler.ContractMode.Runtime

  /**
   * Take the guard of a proved `history` out of the guarded method. Only a clause that is exactly one
   * simple relation can be proved, and so only that kind is ever found in the table.
   */
  private def dropGuard(className: String, methodName: String, clause: String): Unit =
    typing.table_.classes.get(className).foreach { definition =>
      definition.historyGuards.remove((methodName, clause)).foreach { guard =>
        definition.methods.collect { case m: onion.compiler.TypedAST.MethodDefinition if m.name == methodName => m }
          .foreach { m =>
            val kept = m.block.statements.filterNot(_ eq guard)
            if (kept.length != m.block.statements.length) m.setBlock(new onion.compiler.TypedAST.StatementBlock(m.block.location, kept*))
          }
      }
    }

  private def analyze(units: Seq[AST.CompilationUnit], report: Boolean): Unit = {
    reporting = report
    val classes: Map[String, AST.ClassDeclaration] =
      units.flatMap(_.toplevels).collect { case c: AST.ClassDeclaration => c.name -> c }.toMap
    val interfaces: Map[String, AST.InterfaceDeclaration] =
      units.flatMap(_.toplevels).collect { case i: AST.InterfaceDeclaration => i.name -> i }.toMap
    unitOf = units.flatMap(u => u.toplevels.collect { case c: AST.ClassDeclaration => c.name -> u }).toMap
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
    val histories = historiesOf(chain, interfaces)
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
      } if (reporting) typing.reportIn(unitFile, SemanticError.HISTORY_BROKEN_BY_WRITE, w.node, field, s"$field $op old($field)", problem._1, problem._2)
      // After the codegen the solver is asked again to reproduce counterexamples; under `--contracts
      // runtime` it is asked once here, so a history it proves does not need its run-time guard.
      if (!reporting || dropsProvedGuards) proveRest(cls, chain, relations.distinct)
    }
    if (!reporting) uncheckedForms(cls, chain, histories)
    checkInherited(cls, chain, histories, classes, interfaces)
  }

  /**
   * A history binds every method callable on the class, so also the methods it inherits without
   * overriding them (#2603). Each is checked against the relations the class adds: one its
   * superclass is already bound by, on the same field, is checked there, since the superclass runs
   * the same inherited methods (#2781). A write binds a relation only
   * when it writes the field the relation names, not a shadowed one of the same name (#2781). A
   * write the syntax settles is E0109 at the write, in the file that holds it; any other is W0024,
   * since the solver does not prove an inherited body against a subclass's history.
   */
  private def checkInherited(cls: AST.ClassDeclaration, chain: Seq[AST.ClassDeclaration], histories: Seq[AST.Expression],
                             classes: Map[String, AST.ClassDeclaration], interfaces: Map[String, AST.InterfaceDeclaration]): Unit = {
    lazy val perMethod: Seq[(AST.MethodDeclaration, Seq[Write])] =
      for (c <- chain; m <- methodsOf(c) if m.block != null) yield (m, writesIn(m, c))
    // The class that declares the field `f` as `from` sees it, by name.
    def fieldOwner(from: AST.ClassDeclaration, f: String): Option[String] =
      hierarchy(from, classes).find(c => fieldsOf(c).contains(f)).map(_.name)
    // The class that declares `h`; an interface's history reads the fields of `default`.
    def holder(h: AST.Expression, among: Seq[AST.ClassDeclaration], default: AST.ClassDeclaration) =
      among.find(_.histories.exists(_ eq h)).getOrElse(default)
    def relationsOf(hs: Seq[AST.Expression], among: Seq[AST.ClassDeclaration], default: AST.ClassDeclaration) =
      for (h <- hs; (f, op) <- HistoryPreorder.simpleRelations(h)) yield (f, op, fieldOwner(holder(h, among, default), f))
    lazy val added = relationsOf(histories, chain, cls).distinct
    val parentChain = chain.drop(1)
    lazy val parentHistories = historiesOf(parentChain, interfaces)
    lazy val theirs = parentChain.headOption.map(p => relationsOf(parentHistories, parentChain, p).toSet).getOrElse(Set.empty)
    for (Reach(owner, m, via, caller) <- inheritedMethods(cls, chain)) {
      val writes = writesIn(m, owner)
      val file = unitOf.get(owner.name).map(_.sourceFile).orNull
      for ((field, op, declarer) <- added if !theirs((field, op, declarer)) && fieldOwner(owner, field) == declarer) {
        val mine = writes.filter(_.ofField(m, field))
        val broken = mine.flatMap(w => problemOf(op, w.kind, field, perMethod).map(w -> _))
        if (reporting) broken.foreach { case (w, problem) =>
          // Reached through `super` (#2782), the method is overridden, so the hint names the `super` call instead.
          val through = caller.toSeq.flatMap { case (c, cm) => Seq(c, cm) }
          typing.reportIn(file, SemanticError.HISTORY_BROKEN_BY_WRITE, w.node, (Seq[AnyRef](field, s"$field $op old($field)",
            problem._1, problem._2, cls.name, via.name, owner.name) ++ through)*)
        }
        // A raise under `>=` (a lowering under `<=`, a constant nothing undoes) is settled by the syntax.
        else if (broken.isEmpty) mine.find(_.kind == Other).foreach { w =>
          val reason = caller match {
            case Some((c, cm)) => ("super", s"$c.$cm")
            case None          => ("inherited", cls.name)
          }
          unchecked += Unchecked(file, owner, m, field, Some(s"$field $op old($field)"), line = lineOf(w.node),
            fieldType = None, reason = Some(reason), at = w.node.location, via = via.name)
        }
      }
      if (!reporting) {
        val fields = fieldTypes(chain).keySet
        for {
          h <- histories if !parentHistories.exists(sameClause(_, h))
          from = holder(h, chain, cls)
          c <- conjuncts(h) if HistoryPreorder.simpleRelations(c).isEmpty
          named = namesIn(c).filter(f => fields.contains(f) && fieldOwner(from, f) == fieldOwner(owner, f))
          w <- writes.find(w => named.exists(w.ofField(m, _)))
        } unchecked += Unchecked(file, owner, m, w.field, None, line = lineOf(h), fieldType = None, at = w.node.location)
      }
    }
  }

  /** Two clauses spelled the same, wherever they are written. */
  private def sameClause(a: Any, b: Any): Boolean = (a, b) match {
    case (_: onion.compiler.Location, _: onion.compiler.Location) => true
    case (x: Product, y: Product) =>
      x.getClass == y.getClass && x.productArity == y.productArity &&
        x.productIterator.zip(y.productIterator).forall { case (l, r) => sameClause(l, r) }
    case _ => a == b
  }

  /** The names of the fields `c` itself declares. */
  private def fieldsOf(c: AST.ClassDeclaration): Set[String] =
    (c.defaultSection.toSeq ++ c.sections).flatMap(_.members).collect { case f: AST.FieldDeclaration => f.name }.toSet

  /** The histories that bind a class: its own, its superclasses', and its interfaces'. */
  private def historiesOf(chain: Seq[AST.ClassDeclaration], interfaces: Map[String, AST.InterfaceDeclaration]): Seq[AST.Expression] =
    chain.flatMap(_.histories) ++ interfaceHistories(chain.flatMap(_.superInterfaces), interfaces, Set.empty)

  /**
   * A superclass method that runs on a `cls`: the class that declares it, the method, the method a
   * caller reaches it through, and, when it is reached through `super`, the class and method that
   * hold the `super` call (#2782).
   */
  private final case class Reach(owner: AST.ClassDeclaration, m: AST.MethodDeclaration, via: AST.MethodDeclaration,
                                 caller: Option[(String, String)] = None)

  /**
   * The superclasses' methods a `cls` runs, nearest first. A non-private method runs when `cls`
   * does not override it: it is reached by itself, unless a non-private method of the same name
   * and arity overrides it; a private method never overrides and is never overridden (#2781). A
   * private method runs when an inherited method of its own class calls it, directly or through
   * other private methods, and is reached through that caller. A method that runs on `cls` and calls
   * `super.f(..)` also runs the `f` its class inherits, overridden or not, and what that `f` reaches
   * in turn (#2782).
   */
  private def inheritedMethods(cls: AST.ClassDeclaration, chain: Seq[AST.ClassDeclaration]): Seq[Reach] = {
    def sig(m: AST.MethodDeclaration) = (m.name, m.args.size)
    def visible(c: AST.ClassDeclaration) = methodsOf(c).filterNot(isPrivate(c, _))
    def privatesOf(c: AST.ClassDeclaration) = methodsOf(c).filter(m => m.block != null && isPrivate(c, m))
    var overridden = visible(cls).map(sig).toSet
    val plain = chain.drop(1).flatMap { c =>
      val entries = visible(c).filter(m => m.block != null && !overridden(sig(m)))
      val privates = privatesOf(c)
      val reached = for {
        p <- privates
        via <- entries.find(e => reachable(e, privates).exists(_ eq p))
      } yield Reach(c, p, via)
      overridden ++= visible(c).map(sig)
      entries.map(m => Reach(c, m, m)) ++ reached
    }
    // The methods that run on `cls`, by the index of their class in `chain`, are searched for `super` calls.
    def indexOf(c: AST.ClassDeclaration) = chain.indexWhere(_ eq c)
    val seen = scala.collection.mutable.ArrayBuffer.empty[AST.MethodDeclaration]
    seen ++= plain.map(_.m)
    val viaSuper = scala.collection.mutable.ArrayBuffer.empty[Reach]
    var pending: Seq[(Int, AST.MethodDeclaration)] =
      methodsOf(cls).filter(_.block != null).map(0 -> _) ++ plain.map(r => indexOf(r.owner) -> r.m)
    while (pending.nonEmpty) {
      val next = for {
        (i, caller) <- pending
        call <- superCalls(caller)
        (k, target) <- superTarget(call, chain, i, visible).toSeq
        if !seen.exists(_ eq target)
      } yield {
        seen += target
        val c = chain(k)
        val privates = privatesOf(c)
        val reached = reachable(target, privates).filterNot(p => seen.exists(_ eq p))
        seen ++= reached
        val from = Some((chain(i).name, caller.name))
        viaSuper += Reach(c, target, target, from)
        viaSuper ++= reached.map(p => Reach(c, p, target, from))
        (k -> target) +: reached.map(k -> _)
      }
      pending = next.flatten
    }
    plain ++ viaSuper
  }

  /** The `super` calls in `m`'s body, also inside its lambdas, which may run while `m` does. */
  private def superCalls(m: AST.MethodDeclaration): Seq[AST.SuperMethodCall] = {
    val out = Seq.newBuilder[AST.SuperMethodCall]
    def visit(n: AST.Node): Unit = {
      n match {
        case c: AST.SuperMethodCall => out += c
        case c: AST.ClosureExpression => visit(c.body)
        case _ =>
      }
      ASTWalk.visitChildren(n)(visit)
    }
    if (m.block != null) visit(m.block)
    out.result()
  }

  /**
   * The method a `super` call in a method of `chain(from)` runs, with the index of its class, when
   * its body is in this compilation. The type checker's choice decides between overloads of the
   * same name and arity (panel block on #2870). Without it (the call did not type, or its target is
   * not found in `chain`), the nearest method of that name and arity is taken.
   */
  private def superTarget(call: AST.SuperMethodCall, chain: Seq[AST.ClassDeclaration], from: Int,
                          visible: AST.ClassDeclaration => Seq[AST.MethodDeclaration]): Option[(Int, AST.MethodDeclaration)] = {
    val sig = (call.name, call.args.size)
    val above = chain.indices.drop(from + 1)
    def sameSig(m: AST.MethodDeclaration) = m.name == sig._1 && m.args.size == sig._2
    val chosen = resolvedSuper(call).flatMap { r =>
      above.iterator.flatMap(j => visible(chain(j)).find(m => sameSig(m) && isResolved(m, r)).map(j -> _)).nextOption()
    }
    chosen match {
      case Some(found) => Some(found).filter(_._2.block != null)
      case None =>
        // `super.f(..)` runs the nearest `f` above the caller's class.
        above.find(j => visible(chain(j)).exists(sameSig)).flatMap { k =>
          visible(chain(k)).find(m => sameSig(m) && m.block != null).map(k -> _)
        }
    }
  }

  /** The method the type checker chose for a `super` call, also when it is reached from a closure. */
  private def resolvedSuper(call: AST.SuperMethodCall): Option[onion.compiler.TypedAST.Method] = {
    import onion.compiler.TypedAST
    def find(t: TypedAST.Term): Option[TypedAST.Method] = {
      var found: Option[TypedAST.Method] = None
      onion.compiler.TermWalk.exists(t) {
        case c: TypedAST.CallSuper => found = Some(c.method); true
        case _ => false
      }
      found.orElse(t match {
        // Inside a closure the call goes through a private accessor whose body holds the `super` call.
        case c: TypedAST.Call => c.method match {
          case d: TypedAST.MethodDefinition if d.block != null =>
            d.block.statements.iterator.flatMap {
              case e: TypedAST.ExpressionActionStatement => find(e.term)
              case r: TypedAST.Return if r.term != null => find(r.term)
              case _ => None
            }.nextOption()
          case _ => None
        }
        case _ => None
      })
    }
    typedOf(call).collect { case t: TypedAST.Term => t }.flatMap(find)
  }

  private def isResolved(m: AST.MethodDeclaration, r: onion.compiler.TypedAST.Method): Boolean =
    typedOf(m) match {
      case Some(d: onion.compiler.TypedAST.Method) =>
        (d eq r) || (d.name == r.name && d.affiliation.name == r.affiliation.name &&
          d.arguments.map(_.name).sameElements(r.arguments.map(_.name)))
      case _ => false
    }

  private def isPrivate(c: AST.ClassDeclaration, m: AST.MethodDeclaration): Boolean =
    (m.modifiers & AST.M_PRIVATE) != 0 ||
      (c.defaultSection.toSeq ++ c.sections).exists(s => (s.modifiers & AST.M_PRIVATE) != 0 && s.members.exists(_ eq m))

  /** The private methods `from` calls on `this`, directly or through one another. */
  private def reachable(from: AST.MethodDeclaration, privates: Seq[AST.MethodDeclaration]): Seq[AST.MethodDeclaration] = {
    val seen = scala.collection.mutable.ArrayBuffer.empty[AST.MethodDeclaration]
    def visit(m: AST.MethodDeclaration): Unit = {
      val called = callsOnThis(m)
      for (p <- privates if called.contains((p.name, p.args.size)) && !seen.exists(_ eq p)) {
        seen += p
        visit(p)
      }
    }
    visit(from)
    seen.toSeq
  }

  /** The methods `m` calls on this object, by name and arity: `f()`, `this.f()`, `self.f()`. */
  private def callsOnThis(m: AST.MethodDeclaration): Set[(String, Int)] = {
    val out = Set.newBuilder[(String, Int)]
    def visit(n: AST.Node): Unit = {
      n match {
        case AST.UnqualifiedMethodCall(_, name, args, _) => out += ((name, args.size))
        case AST.MethodCall(_, null | (_: AST.CurrentInstance), name, args, _) => out += ((name, args.size))
        case _ =>
      }
      ASTWalk.visitChildren(n)(visit)
    }
    if (m.block != null) visit(m.block)
    out.result()
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
      // A history over method calls (`x() >= old(x())`) names no field, so any write may break it (#2735).
      w <- writesIn(m, cls).find(w => if (named.isEmpty) fields.contains(w.field) else named.exists(w.ofField(m, _)))
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
    // The methods a body may call: this class's and its superclasses' own, nearest first.
    val callees: Map[String, AST.MethodDeclaration] =
      chain.reverse.flatMap(methodsOf).map(m => m.name -> m).toMap
    for {
      m <- methodsOf(cls) if m.block != null && !writesIn(m, cls).exists(w => relations.exists(r => w.ofField(m, r._1)) && brokenSyntactically(w, relations, chain))
      (field, op) <- relations
    } hiddenWrite(writesIn(m, cls).filter(_.ofField(m, field))) match {
      // A write in a lambda, which the body-only checks cannot follow, is said as such at its line (#2599).
      case Some(w) =>
        unchecked += Unchecked(unitFile, cls, m, field, Some(s"$field $op old($field)"), line = lineOf(w.node),
          fieldType = None, reason = Some(("lambda", "")), at = w.node.location)
      case None => HistoryProver.prove(m, field, op, intFields, invariants, callees, builtCallees(m)) match {
      case HistoryProver.Counterexample(before) =>
        if (collected != null) collected += Repro(unitFile, cls, m, field, op, before)
      // A proof may lean on a class invariant only once something checks that invariant: until
      // then a constructor can break it unseen (#2602). So the solver is asked again without the
      // invariants; a proof that needs them is W0024, and the runtime guard stays. Only a method
      // that writes the field can break the history, so one that only reads it is not asked (#2671).
      case HistoryProver.Proved if invariants.nonEmpty && writesIn(m, cls).exists(_.ofField(m, field)) &&
          HistoryProver.prove(m, field, op, intFields, Nil, callees, builtCallees(m)) != HistoryProver.Proved =>
        unchecked += Unchecked(unitFile, cls, m, field, Some(s"$field $op old($field)"), line = 0,
          fieldType = None, reason = Some(("invariant", "")))
      case HistoryProver.Proved =>
        proved += s"${cls.name}.${m.name}: $field $op old($field)"
        if (dropsProvedGuards) dropGuard(cls.name, m.name, s"$field $op old($field)")
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

  /**
   * The contracts of methods of types built in an earlier compilation, for the parameters of `m`
   * (RFC #2557, across modules). They are read from the `@onion.Contract` in each class file; a
   * type with none, or one this compilation defines itself, contributes nothing.
   */
  private def builtCallees(m: AST.MethodDeclaration): Map[(String, String), HistoryProver.Callee] =
    (for {
      a <- m.args
      typeName <- Option(a.typeRef).map(_.desc).collect { case AST.ReferenceType(n, _) => n }.toList.distinct
      classType <- builtType(typeName).toList
      method <- classType.methods
      ref <- Some(method).collect { case r: onion.compiler.environment.AsmRefs.AsmMethodRef => r }
      c = ref.contract if !c.isEmpty
      callee <- Some(HistoryProver.Callee(c.params, c.requires, c.ensures))
    } yield (typeName, method.name) -> callee).toMap

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
        // `*=`, `/=`, `%=` and the bitwise forms move the field either way; the solver decides (#2603).
        case a: (AST.MultiplicationAssignment | AST.DivisionAssignment | AST.ModuloAssignment |
                 AST.BitAndAssignment | AST.BitOrAssignment | AST.XorAssignment | AST.LeftShiftAssignment |
                 AST.MathRightShiftAssignment | AST.LogicalRightShiftAssignment) =>
          add(a.lhs, a, _ => Other)
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
   * the method (#2599), or a proof that holds only by assuming a class invariant (`invariant`, #2602).
   * `via` is, for an inherited method (`inherited`), the method a caller reaches it through (#2781).
   */
  final case class Unchecked(sourceFile: String, cls: AST.ClassDeclaration, method: AST.MethodDeclaration,
                             field: String, history: Option[String], line: Int, fieldType: Option[String],
                             reason: Option[(String, String)] = None, at: onion.compiler.Location = null,
                             via: String = null)
}
