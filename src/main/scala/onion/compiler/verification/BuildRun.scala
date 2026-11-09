package onion.compiler.verification

import onion.compiler.{CompilerConfig, Modifier, TypedAST}
import onion.compiler.effects.{Effect, EffectInference, EffectTable}

/**
 * What the build may run, and how long it waits (#2601, `docs/design/contracts.md` §6).
 *
 * The build runs user code for a closed or sampled predicate and for an `E0112` replay. It may
 * only when the code's inferred effects are at most `mutate`: anything from the outside world
 * (`read`, `write`, `net`, `exec`, `env`, `clock`, `rand`, `console`) or `unknown` is not run.
 * A run that does not finish within the configured time is given up on, so it cannot hang the
 * build. (The JVM cannot stop such a thread; it is a daemon thread, so it does not keep the
 * compiler's process alive.)
 */
final class BuildRun(config: CompilerConfig, typed: Seq[TypedAST.ClassDefinition]) {
  private lazy val table: EffectTable.Table = EffectTable.forClasspath(config.classPath).table
  private lazy val unitEffects: Map[EffectInference.Unit0, Set[Effect]] =
    EffectInference.inferUnits(typed, table, callbacks = true)._2

  private def owners(owner: Option[String]): Seq[TypedAST.ClassDefinition] = owner match {
    case Some(o) => typed.filter(cd => cd.name == o || cd.name.endsWith("." + o))
    case None    => typed
  }

  /**
   * The outside effects of a method, or `unknown` when it cannot be found. Every overload
   * with that name and arity counts, so the check never looks at a different method than
   * the run calls (#2601). The build calls it from outside its class, so the static
   * initializers of the class and its superclasses run first and count too.
   */
  def methodEffects(owner: Option[String], name: String, arity: Int, static: Boolean): Set[Effect] =
    source(owner, name, arity, static)._1

  /**
   * The effects `methodEffects` reports, and where they come from: `Some(Left(c))` when a
   * static initializer of `c` has one, `Some(Right(call))` for the first call in the body
   * that has one, `None` when the body has it directly or the method is missing.
   */
  def source(owner: Option[String], name: String, arity: Int, static: Boolean): (Set[Effect], Option[Either[String, String]]) = {
    val found = owners(owner).flatMap(cd => cd.methods.collect {
      case m: TypedAST.MethodDefinition if m.name == name && m.arguments.length == arity &&
        Modifier.isStatic(m.modifier) == static => cd -> m
    })
    if (found.isEmpty) return (Set(Effect.Unknown), None)
    val inits = found.map(_._1).distinct.flatMap(chain).distinct
    val initEffects = inits.map(cd => cd -> initOf(cd))
    val all = found.flatMap { case (_, m) => outside(unitEffects.getOrElse(m, Set(Effect.Unknown))) }.toSet ++
      initEffects.flatMap(_._2)
    val fromInit = initEffects.collectFirst { case (cd, eff) if eff.nonEmpty => Left(cd.name) }
    def fromCall = found.iterator.flatMap { case (cd, m) =>
      if (m.getBlock == null) Iterator.empty
      else EffectInference.callSites(m.getBlock, typed, unitEffects, table, cd, callbacks = true).iterator
        .collectFirst { case site if outside(site.effects).nonEmpty => site.initializerOf.toLeft(site.callee) }
    }.nextOption()
    (all, if (all.isEmpty) None else fromInit.orElse(fromCall))
  }

  /**
   * The outside effects of making sample arguments for a predicate's parameters, and the first
   * program class they come from (the panel's block on #2610). A parameter of a program class is
   * built by calling its constructor, which first runs its static initializers; a component of a
   * program class is built the same way, in turn; and a failing sample is shown with its
   * `toString`. Every constructor counts, so the check never looks at a different one than the
   * sampler calls. Empty when no parameter is of a program class.
   */
  def sampleEffects(owner: Option[String], name: String, arity: Int, static: Boolean): (Set[Effect], Option[String]) = {
    val params = owners(owner).flatMap(cd => cd.methods.collect {
      case m: TypedAST.MethodDefinition if m.name == name && m.arguments.length == arity &&
        Modifier.isStatic(m.modifier) == static => m
    }).flatMap(_.arguments.toSeq)
    val seen = scala.collection.mutable.LinkedHashSet[TypedAST.ClassDefinition]()
    def visit(t: TypedAST.Type): scala.Unit = t match {
      case ot: TypedAST.ObjectType => byName.get(ot.name).foreach { cd =>
        if (seen.add(cd)) cd.constructors.foreach {
          case c: TypedAST.ConstructorDefinition => c.arguments.foreach(visit)
          case _ =>
        }
      }
      // The sampler builds a `Foo?` the same way as a `Foo` (panel block on #2610 at f22b1e3).
      case nt: TypedAST.NullableType => visit(nt.innerType)
      case _ =>
    }
    params.foreach(visit)
    val perClass = seen.toSeq.map(cd => cd -> classSampleEffects(cd))
    (perClass.flatMap(_._2).toSet, perClass.collectFirst { case (cd, eff) if eff.nonEmpty => cd.name })
  }

  /** A sampled class's constructors, its static initializers, and its `toString`. */
  private def classSampleEffects(cd: TypedAST.ClassDefinition): Set[Effect] = {
    val ctors = cd.constructors.toSeq.collect {
      case c: TypedAST.ConstructorDefinition => outside(unitEffects.getOrElse(c, Set(Effect.Unknown)))
    }.flatten
    val shown = chain(cd).flatMap(_.methods.collect {
      case m: TypedAST.MethodDefinition if m.name == "toString" && m.arguments.isEmpty &&
        !Modifier.isStatic(m.modifier) && unitEffects.contains(m) => outside(unitEffects(m))
    }.flatten)
    (ctors ++ chain(cd).flatMap(initOf) ++ shown).toSet
  }

  /** The outside effects of a class's no-argument constructor and its static initializers; empty when it has neither. */
  def constructorEffects(owner: String): Set[Effect] = {
    val cds = owners(Some(owner))
    val ctor = cds.iterator.flatMap(cd => cd.constructors.iterator).collectFirst {
      case c: TypedAST.ConstructorDefinition if c.arguments.isEmpty => outside(unitEffects.getOrElse(c, Set(Effect.Unknown)))
    }.getOrElse(Set.empty)
    ctor ++ cds.flatMap(chain).distinct.flatMap(initOf)
  }

  private lazy val byName: Map[String, TypedAST.ClassDefinition] = typed.map(cd => cd.name -> cd).toMap

  /** A class and each program superclass: the JVM initializes the superclass first. */
  private def chain(cd: TypedAST.ClassDefinition): Seq[TypedAST.ClassDefinition] = {
    val out = scala.collection.mutable.Buffer[TypedAST.ClassDefinition]()
    var next: Option[TypedAST.ClassDefinition] = Some(cd)
    while (next.isDefined && !out.exists(_ eq next.get)) {
      out += next.get
      next = Option(next.get.superClass).flatMap(sc => byName.get(sc.name))
    }
    out.toSeq
  }

  private def initOf(cd: TypedAST.ClassDefinition): Set[Effect] =
    if (cd.staticInitializers.isEmpty) Set.empty
    else outside(unitEffects.getOrElse(EffectInference.ClassInit(cd), Set(Effect.Unknown)))

  private def outside(effects: Set[Effect]): Set[Effect] = effects - Effect.Mutate

  /** `Some(result)` when `body` finished in time, `None` when the build stopped waiting for it. */
  def timed[A](loader: ClassLoader)(body: => A): Option[Either[Throwable, A]] = {
    @volatile var result: Option[Either[Throwable, A]] = None
    val thread = new Thread(() => {
      result = Some(try Right(body) catch { case t: Throwable => Left(t) })
    }, "onion-contract-run")
    thread.setDaemon(true)
    thread.setContextClassLoader(loader)
    thread.start()
    thread.join(math.max(1L, config.contractTimeoutMillis))
    if (thread.isAlive) None else result
  }
}

object BuildRun {
  /** `console, read`: the effects a W0023 names, in a fixed order. */
  def render(effects: Set[Effect]): String = effects.toSeq.map(_.name).sorted.mkString(", ")

  /** W0023's text for a predicate with outside effects, naming where they come from when it is not the body itself. */
  def notRunMessage(name: String, effects: Set[Effect], from: Option[Either[String, String]]): String = from match {
    case Some(Left(cls))   => onion.compiler.toolbox.Message("warning.predicateNotRun.initializer", Array[Any](name, render(effects), cls))
    case Some(Right(call)) => onion.compiler.toolbox.Message("warning.predicateNotRun.call", Array[Any](name, render(effects), call))
    case None              => onion.compiler.toolbox.Message("warning.predicateNotRun.effects", name, render(effects))
  }

  /** W0023's text for an `E0112` replay of `method` that was not run because it has outside effects. */
  def replayNotRunMessage(method: String, history: String, effects: Set[Effect]): String =
    onion.compiler.toolbox.Message("warning.replayNotRun.effects", Array[Any](method, history, render(effects)))

  /** W0023's text for an `E0112` replay of `method` that did not finish within `millis`. */
  def replayTimeoutMessage(method: String, history: String, millis: Long): String =
    onion.compiler.toolbox.Message("warning.replayNotRun.timeout", Array[Any](method, history, millis.toString))

  /** W0023's text for a predicate whose sample arguments of program class `cls` cannot be made without an outside effect. */
  def notSampledMessage(name: String, effects: Set[Effect], cls: String): String =
    onion.compiler.toolbox.Message("warning.predicateNotRun.sample", Array[Any](name, render(effects), cls))
}
