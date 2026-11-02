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
  private lazy val unitEffects: Map[EffectInference.Unit0, Set[Effect]] =
    EffectInference.inferUnits(typed, EffectTable.forClasspath(config.classPath).table)._2

  private def owners(owner: Option[String]): Seq[TypedAST.ClassDefinition] = owner match {
    case Some(o) => typed.filter(cd => cd.name == o || cd.name.endsWith("." + o))
    case None    => typed
  }

  /**
   * The outside effects of a method, or `unknown` when it cannot be found. The build calls it
   * from outside its class, so the class's static initializer runs first and counts too.
   */
  def methodEffects(owner: Option[String], name: String, arity: Int, static: Boolean): Set[Effect] =
    owners(owner).iterator.flatMap(cd => cd.methods.map(cd -> _)).collectFirst {
      case (cd, m: TypedAST.MethodDefinition) if m.name == name && m.arguments.length == arity &&
        Modifier.isStatic(m.modifier) == static =>
        outside(unitEffects.getOrElse(m, Set(Effect.Unknown)) ++ initEffects(cd))
    }.getOrElse(Set(Effect.Unknown))

  /** The outside effects of a class's no-argument constructor and its static initializer; empty when it has neither. */
  def constructorEffects(owner: String): Set[Effect] =
    owners(Some(owner)).iterator.flatMap(cd => cd.constructors.map(cd -> _)).collectFirst {
      case (cd, c: TypedAST.ConstructorDefinition) if c.arguments.isEmpty =>
        outside(unitEffects.getOrElse(c, Set(Effect.Unknown)) ++ initEffects(cd))
    }.getOrElse(owners(Some(owner)).headOption.map(cd => outside(initEffects(cd))).getOrElse(Set.empty))

  private def initEffects(cd: TypedAST.ClassDefinition): Set[Effect] =
    if (cd.staticInitializers.isEmpty) Set.empty
    else unitEffects.getOrElse(EffectInference.ClassInit(cd), Set(Effect.Unknown))

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
}
