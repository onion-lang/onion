package onion.compiler.verification

import onion.compiler.{CompileError, CompileWarning, CompiledClass, CompilerConfig, ContractMode, OnionClassLoader, SemanticError, WarningCategory, WarningLevel}
import onion.compiler.contracts.ContractTable
import onion.compiler.exceptions.CompilationException
import onion.compiler.pipeline.{CompilerPhase, PhaseContext}
import onion.compiler.toolbox.Message

import java.lang.reflect.{Modifier, ParameterizedType}
import scala.collection.mutable.ArrayBuffer

/**
 * The contract of `onion.Shape` (RFC #2557, design 5.1), checked on every concrete implementation.
 *
 * L1, the round-trip, is the contract: `parse(print(v)) == Ok(v)` for each value `v` the shape can
 * print, which is `canPrint()` and `printable(v)`. L2 (`print(parse(t)) == t`) is false in general
 * and is never asserted. The values are sampled with the sampler a predicate with parameters uses.
 *
 *  - a value that does not round-trip is `E0114`, with the value and the seed that reproduces it;
 *  - a type that cannot be sampled is the warning `W0022`, never a silent pass;
 *  - a shape that cannot print (`canPrint() == false`) owes nothing.
 *
 * Only with `--contracts check`, and not in the editor (`runContracts = false`).
 */
final class ShapeContractPhase(config: CompilerConfig) extends CompilerPhase[Seq[CompiledClass], Seq[CompiledClass]] {
  def name: String = "ShapeContract"

  /** The interface the table holds a round-trip entry for; its method names come from the table. */
  private val entry: ContractTable.RoundTrip =
    ContractTable.forInterface("onion.Shape").collect { case r: ContractTable.RoundTrip => r }.get
  private val ShapeName = entry.interface

  def run(classes: Seq[CompiledClass], ctx: PhaseContext): Seq[CompiledClass] = {
    if (config.contracts != ContractMode.Check || !config.runContracts) return classes
    val loader = new OnionClassLoader(classOf[OnionClassLoader].getClassLoader, config.classPath, classes)
    val thread = Thread.currentThread
    val previous = thread.getContextClassLoader
    thread.setContextClassLoader(loader)
    val errors = ArrayBuffer.empty[CompileError]
    val warnings = ArrayBuffer.empty[CompileWarning]
    val build = new BuildRun(config, ctx.debugArtifacts.typedClasses.getOrElse(Nil))
    try {
      for (cc <- classes) {
        val clazz = try Some(Class.forName(cc.className, false, loader)) catch { case _: Throwable => None }
        clazz.filter(isConcreteShape).foreach(check(_, cc, loader, build, errors, warnings))
      }
    } finally thread.setContextClassLoader(previous)
    if (config.warningLevel != WarningLevel.Off)
      ctx.addWarnings(warnings.toSeq.filterNot(w => config.suppressedWarnings.contains(w.category)))
    if (errors.nonEmpty) throw new CompilationException(errors.toSeq)
    classes
  }

  private def isConcreteShape(c: Class[?]): Boolean =
    !c.isInterface && !Modifier.isAbstract(c.getModifiers) && c.getInterfaces.exists(_.getName == ShapeName)

  /** The `T` of `Shape<T>` as the class declares it, or none when it is not a plain class. */
  private def valueType(c: Class[?]): Option[Class[?]] =
    c.getGenericInterfaces.collectFirst {
      case p: ParameterizedType if p.getRawType.getTypeName == ShapeName => p.getActualTypeArguments.head
    }.collect { case k: Class[?] => k }

  private def check(c: Class[?], cc: CompiledClass, loader: ClassLoader, build: BuildRun,
                    errors: ArrayBuffer[CompileError], warnings: ArrayBuffer[CompileWarning]): Unit = {
    val simple = c.getSimpleName
    val file = cc.className
    def warn(why: String): Unit =
      warnings += CompileWarning(file, null, WarningCategory.ShapeNotSampled,
        s"the contract of Shape '$simple' was not checked: $why")
    val tpe = valueType(c).getOrElse { warn("its value type is not a plain class"); return }
    // The build runs only what cannot reach the outside world (#2733, #2601): the rest is said, not run.
    shapeEffects(c, cc, tpe, build).foreach { msg =>
      warnings += CompileWarning(file, null, WarningCategory.PredicateNotRun, msg)
      return
    }
    // A run that does not finish in time is given up on, so a looping `parse` cannot hang the build.
    // The run only computes its finding; it is recorded here, so a run given up on adds nothing later.
    build.timed(loader)(runRoundTrip(c, tpe, loader)) match {
      case None =>
        warnings += CompileWarning(file, null, WarningCategory.PredicateNotRun,
          Message("warning.shapeNotRun.timeout", Array[Any](simple, config.contractTimeoutMillis.toString)))
      case Some(Left(_)) => warn("it threw while being checked")
      case Some(Right(None)) =>
      case Some(Right(Some(Left(why)))) => warn(why)
      case Some(Right(Some(Right(why)))) =>
        errors += CompileError(file, null,
          Message("error.semantic.shapeContractBroken", Array[Any](simple, why,
            s"--contract-seed ${config.contractSeed} --contract-samples ${config.contractSamples}")),
          Some(SemanticError.SHAPE_CONTRACT_BROKEN.errorCode))
    }
  }

  /** `None` when the round-trip holds or is not checkable now; `Left` why it was not checked; `Right` how it broke. */
  private def runRoundTrip(c: Class[?], tpe: Class[?], loader: ClassLoader): Option[Either[String, String]] = {
    val shape = try {
      val ctor = c.getDeclaredConstructor()
      ctor.setAccessible(true)
      ctor.newInstance().asInstanceOf[AnyRef]
    } catch { case _: Throwable => return Some(Left("it has no no-argument constructor")) }
    val values = ArgGenerator.generateValues(tpe, loader, config.contractSamples, config.contractSeed)
      .getOrElse { return Some(Left(s"values of type ${tpe.getSimpleName} cannot be sampled")) }
    // The round-trip calls the interface's own methods, so the JVM dispatches each call to the
    // instance method that implements it (or the library default). That is exactly what
    // `shapeEffects` checks: a static method or an unrelated overload of the same name is never
    // run (panel block at 9ba150d).
    val iface = c.getInterfaces.find(_.getName == ShapeName).get
    def own(name: String, params: Class[?]*) = try Some(iface.getMethod(name, params*)) catch { case _: NoSuchMethodException => None }
    val canPrint = iface.getMethod(entry.canPrint)
    val print = own(entry.print, classOf[Object])
    val printable = own(entry.printable, classOf[Object])
    val parse = own(entry.parse, classOf[String])
    (print, parse) match {
      case (Some(p), Some(r)) =>
        val canPrintNow: Boolean = try canPrint.invoke(shape).asInstanceOf[java.lang.Boolean].booleanValue catch { case _: Throwable => false }
        if (!canPrintNow) None
        else values.iterator.map(v => roundTrip(shape, p, r, printable, v)).collectFirst { case Some(why) => Right(why) }
      case _ => Some(Left("it does not declare parse and print"))
    }
  }

  /**
   * W0023's text when something the round-trip calls (the constructor, `canPrint`, `printable`,
   * `print`, `parse`, or making and showing a sample of the value type) has an effect beyond
   * `mutate`, naming the first place it comes from. `None` when the build may run it.
   */
  private def shapeEffects(c: Class[?], cc: CompiledClass, tpe: Class[?], build: BuildRun): Option[String] = {
    val simple = c.getSimpleName
    def notRun(effects: Set[onion.compiler.effects.Effect], where: String) =
      Message("warning.shapeNotRun.effects", Array[Any](simple, BuildRun.render(effects), where))
    val ctor = build.constructorEffects(cc.className)
    if (ctor.nonEmpty) return Some(notRun(ctor, Message("warning.shapeNotRun.where.constructor")))
    // The value type first: a `parse` that builds a value inherits its constructor's effect, and the
    // place to fix that is the value type, not the shape.
    val (sample, from) = build.sampleEffectsOfClass(tpe.getName)
    if (sample.nonEmpty) {
      val cls = from.map(_.split('.').last).getOrElse(tpe.getSimpleName)
      return Some(Message("warning.shapeNotRun.sample", Array[Any](simple, BuildRun.render(sample), cls)))
    }
    // Every instance overload counts (`parse` takes the text and an origin); a static method is
    // never run, so it does not. A method declared on a library interface (`onion.Shape`'s own
    // `printable`) is library code with no effect; a default from a program interface is the program's code and is checked like any other (panel block at e8fef6a).
    Seq(entry.canPrint, entry.printable, entry.print, entry.parse).iterator.flatMap { n =>
      c.getMethods.iterator.filter(m => m.getName == n && !m.isBridge && !Modifier.isStatic(m.getModifiers) &&
        (!m.getDeclaringClass.isInterface || build.isProgramClass(m.getDeclaringClass.getName)))
        .map(m => n -> build.methodEffects(Some(m.getDeclaringClass.getName), n, m.getParameterCount, static = false))
    }.collectFirst { case (n, effects) if effects.nonEmpty => notRun(effects, s"`$n`") }
  }

  /** `None` when `v` is not printable or round-trips; otherwise how it failed. */
  private def roundTrip(shape: AnyRef, print: java.lang.reflect.Method, parse: java.lang.reflect.Method,
                        printable: Option[java.lang.reflect.Method], v: AnyRef): Option[String] =
    try {
      val admitted = printable.forall(_.invoke(shape, v).asInstanceOf[java.lang.Boolean].booleanValue)
      if (!admitted) None
      else {
        val text = print.invoke(shape, v).asInstanceOf[String]
        val back = parse.invoke(shape, text)
        val isOk = back.getClass.getMethod("isOk").invoke(back).asInstanceOf[java.lang.Boolean].booleanValue
        val got = if (isOk) back.getClass.getMethod("get").invoke(back) else null
        if (isOk && java.util.Objects.equals(got, v)) None
        else Some(s"for ${v} the text \"$text\" reads back as ${if (isOk) String.valueOf(got) else "a defect"}")
      }
    } catch {
      case e: java.lang.reflect.InvocationTargetException =>
        Some(s"for ${v} it threw ${Option(e.getCause).getOrElse(e).getClass.getSimpleName}")
      case e: Throwable => Some(s"for ${v} it threw ${e.getClass.getSimpleName}")
    }
}
