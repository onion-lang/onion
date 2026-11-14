package onion.compiler.verification

import onion.compiler.{AST, CompileError, CompileWarning, CompiledClass, CompilerConfig, ContractMode, Modifier, OnionClassLoader, SemanticError, WarningCategory, WarningLevel}
import onion.compiler.exceptions.CompilationException
import onion.compiler.pipeline.{CompilerPhase, PhaseContext}
import onion.compiler.toolbox.Message

import java.lang.reflect.Method
import scala.collection.mutable.ArrayBuffer

/**
 * Runs the closed predicates of a program at build time (RFC #2557, design 7.2): the replacement
 * for `example`. A closed predicate is a `static def` with no parameters that returns `Boolean`
 * and carries the clause `ensures result`, in a class, record or at the top level:
 *
 * {{{
 * def example_1(): Boolean ensures result = 1 + 1 == 2
 * }}}
 *
 * A predicate that returns `false` is `E0113`; one that throws is also `E0113`, naming the
 * exception. Only with `--contracts check`. The predicate runs once, in a class loader of its own.
 * One whose effects reach the outside world, or that does not finish in time, is not run and
 * gets W0023 instead (#2601, [[BuildRun]]).
 */
final class ClosedPredicatePhase(config: CompilerConfig) extends CompilerPhase[Seq[CompiledClass], Seq[CompiledClass]] {
  def name: String = "ClosedPredicate"

  /** `owner` is the class to look in; for a top-level function (`topLevel`) it is the unit's own top-level class. */
  private final case class Candidate(owner: Option[String], name: String, arity: Int, sourceFile: String, location: onion.compiler.Location, topLevel: Boolean = false)

  def run(classes: Seq[CompiledClass], ctx: PhaseContext): Seq[CompiledClass] = {
    if (config.contracts != ContractMode.Check || !config.runContracts) return classes
    val units = ctx.debugArtifacts.rewrittenUnits.getOrElse(return classes)
    val candidates = units.flatMap(candidatesOf)
    if (candidates.isEmpty) return classes

    val loader = new OnionClassLoader(classOf[OnionClassLoader].getClassLoader, config.classPath, classes)
    val thread = Thread.currentThread
    val previous = thread.getContextClassLoader
    thread.setContextClassLoader(loader)
    val errors = ArrayBuffer.empty[CompileError]
    val warnings = ArrayBuffer.empty[CompileWarning]
    val build = new BuildRun(config, ctx.debugArtifacts.typedClasses.getOrElse(Nil))
    def notRun(c: Candidate, message: String): Unit =
      warnings += CompileWarning(c.sourceFile, c.location, WarningCategory.PredicateNotRun, message)
    try {
      for (c <- candidates) {
        // The build runs only what cannot reach the outside world (#2601); the rest is said, not run.
        val (effects, from) = build.source(c.owner, c.name, c.arity, static = true)
        val (sampled, sampledFrom) = build.sampleEffects(c.owner, c.name, c.arity, static = true)
        if (effects.nonEmpty) notRun(c, BuildRun.notRunMessage(c.name, effects, from))
        else if (sampled.nonEmpty) notRun(c, BuildRun.notSampledMessage(c.name, sampled, sampledFrom.getOrElse("?")))
        else build.timed(loader)(runOne(c, classes, loader)) match {
          case None =>
            notRun(c, Message("warning.predicateNotRun.timeout", c.name, config.contractTimeoutMillis.toString))
          case Some(Left(e)) =>
            errors += falseOrThrew(c, Message("error.semantic.predicateReason.threw",
              Array[Any](e.getClass.getSimpleName, String.valueOf(e.getMessage), "")))
          case Some(Right(outcome)) =>
            outcome.foreach {
              case Left(type0) => errors += notSampled(c, type0)
              case Right(why)  => errors += falseOrThrew(c, why)
            }
        }
      }
    } finally thread.setContextClassLoader(previous)
    if (config.warningLevel != WarningLevel.Off)
      ctx.addWarnings(warnings.toSeq.filterNot(w => config.suppressedWarnings.contains(w.category)))
    if (errors.nonEmpty) throw new CompilationException(errors.toSeq)
    classes
  }

  private def falseOrThrew(c: Candidate, why: String): CompileError =
    CompileError(c.sourceFile, c.location,
      Message(if (c.arity == 0) "error.semantic.closedPredicateFalse" else "error.semantic.sampledPredicateFalse",
        Array[Any](c.name, why)),
      Some(SemanticError.CLOSED_PREDICATE_FALSE.errorCode))

  /** The predicate was not run at all: say so, rather than claiming it "did not hold" (#2752). */
  private def notSampled(c: Candidate, typeName: String): CompileError =
    CompileError(c.sourceFile, c.location,
      Message("error.semantic.predicateNotSampled", Array[Any](c.name, typeName)),
      Some(SemanticError.CLOSED_PREDICATE_FALSE.errorCode))

  /** An argument's text for the report; a `toString` that throws must not stand in for the predicate failing. */
  private def show(a: AnyRef): String =
    try String.valueOf(a)
    catch { case e: Throwable => s"<${a.getClass.getSimpleName}: toString threw ${e.getClass.getSimpleName}>" }

  /** Only `ensures result` itself marks a predicate. `ensures result == (x > 0)` describes a function and is left alone. */
  private def assertsResult(clauses: List[AST.Expression]): Boolean =
    clauses.exists { case AST.Id(_, "result") => true; case _ => false }

  private def isBoolean(t: AST.TypeNode): Boolean = t != null && t.desc == AST.PrimitiveType(AST.KBoolean)

  private def candidatesOf(unit: AST.CompilationUnit): Seq[Candidate] =
    unit.toplevels.flatMap {
      case f: AST.FunctionDeclaration if isBoolean(f.returnType) && assertsResult(f.ensures) =>
        Seq(Candidate(Some(topClassOf(unit)), f.name, f.args.size, unit.sourceFile, f.location, topLevel = true))
      case c: AST.ClassDeclaration =>
        (c.defaultSection.toSeq ++ c.sections).flatMap(_.members).collect {
          case m: AST.MethodDeclaration if qualifies(m) => Candidate(Some(c.name), m.name, m.args.size, unit.sourceFile, m.location)
        }
      case r: AST.RecordDeclaration =>
        r.sections.flatMap(_.members).collect {
          case m: AST.MethodDeclaration if qualifies(m) => Candidate(Some(r.name), m.name, m.args.size, unit.sourceFile, m.location)
        }
      case _ => Nil
    }

  /** The class the compiler gives a unit's top-level code (`<file>Main`, inside the unit's module). */
  private def topClassOf(unit: AST.CompilationUnit): String = {
    val simple = onion.compiler.toolbox.Paths.cutExtension(unit.sourceFile) + "Main"
    if (unit.module != null) unit.module.name + "." + simple else simple
  }

  private def qualifies(m: AST.MethodDeclaration): Boolean =
    Modifier.isStatic(m.modifiers) && isBoolean(m.returnType) && assertsResult(m.ensures)

  /** `None` when the predicate holds (or cannot be found); the reason when it is false or throws. */
  private def runOne(c: Candidate, classes: Seq[CompiledClass], loader: ClassLoader): Option[Either[String, String]] = {
    val owners = c.owner match {
      case Some(o) if c.topLevel => classes.filter(_.className == o)
      case Some(o) => classes.filter(cc => cc.className == o || cc.className.endsWith("." + o))
      case None    => classes
    }
    val method: Option[Method] = owners.iterator.flatMap { cc =>
      try Class.forName(cc.className, false, loader).getDeclaredMethods.iterator
          .find(m => m.getName == c.name && m.getParameterCount == c.arity && java.lang.reflect.Modifier.isStatic(m.getModifiers) &&
            (m.getReturnType == java.lang.Boolean.TYPE))
      catch { case _: Throwable => None }
    }.nextOption()
    method.flatMap { m =>
      m.setAccessible(true)
      val types = m.getParameterTypes
      val perParam = types.map(t => ArgGenerator.generateValues(t, loader, config.contractSamples, config.contractSeed).orNull)
      val bad = perParam.indexWhere(_ == null)
      // A parameter that cannot be sampled must not pass silently: that would look like a predicate that held.
      if (bad >= 0) Some(Left(types(bad).getSimpleName))
      else combos(perParam).iterator.map(args => call(m, args)).collectFirst { case Some(why) => Right(why) }
    }
  }

  /** `None` when the call returns true; otherwise why not, with the arguments and how to reproduce them. */
  private def call(m: Method, args: Array[AnyRef]): Option[String] = {
    val shown = if (args.isEmpty) ""
      else Message("error.semantic.predicateReason.at",
        Array[Any](args.map(show).mkString(", "), config.contractSeed.toString, config.contractSamples.toString))
    try {
      if (m.invoke(null, args*).asInstanceOf[java.lang.Boolean]) None else Some(Message("error.semantic.predicateReason.false", shown))
    } catch {
      case e: java.lang.reflect.InvocationTargetException =>
        val cause = Option(e.getCause).getOrElse(e)
        Some(Message("error.semantic.predicateReason.threw",
          Array[Any](cause.getClass.getSimpleName, String.valueOf(cause.getMessage), shown)))
      case e: Throwable => Some(Message("error.semantic.predicateReason.threwBare", e.getClass.getSimpleName, shown))
    }
  }

  /** No parameters: one call. Otherwise a diagonal over the samples plus a one-at-a-time boundary sweep, capped. */
  private def combos(perParam: Array[List[AnyRef]]): List[Array[AnyRef]] =
    if (perParam.isEmpty) List(Array.empty[AnyRef])
    else {
      val arity = perParam.length
      val minLen = perParam.map(_.length).min
      val diag = (0 until minLen).map(i => Array.tabulate(arity)(p => perParam(p)(i))).toList
      val sweep = (0 until arity).flatMap { p =>
        perParam(p).indices.map(k => Array.tabulate(arity)(q => if (q == p) perParam(q)(k) else perParam(q)(0)))
      }.toList
      (diag ++ sweep).take(config.contractSamples)
    }
}
