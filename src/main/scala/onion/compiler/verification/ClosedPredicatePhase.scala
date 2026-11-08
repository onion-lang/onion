package onion.compiler.verification

import onion.compiler.{AST, CompileError, CompiledClass, CompilerConfig, ContractMode, Modifier, OnionClassLoader, SemanticError}
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
 */
final class ClosedPredicatePhase(config: CompilerConfig) extends CompilerPhase[Seq[CompiledClass], Seq[CompiledClass]] {
  def name: String = "ClosedPredicate"

  /**
   * `owner` is the class to look in; for a top-level function it is the unit's own top-level class.
   * `topLevelState` is true when that unit declares top-level `val`/`var`s: the script's `start`
   * method sets them, and the build does not run it, so a predicate reading one sees it unset.
   */
  private final case class Candidate(owner: Option[String], name: String, arity: Int, sourceFile: String, location: onion.compiler.Location, topLevel: Boolean = false, topLevelState: Boolean = false)

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
    try {
      for (c <- candidates) {
        val outcome = runOne(c, classes, loader)
        outcome.foreach { why =>
          errors += CompileError(c.sourceFile, c.location,
            Message("error.semantic.closedPredicateFalse", Array[Any](c.name, why)),
            Some(SemanticError.CLOSED_PREDICATE_FALSE.errorCode))
        }
      }
    } finally thread.setContextClassLoader(previous)
    if (errors.nonEmpty) throw new CompilationException(errors.toSeq)
    classes
  }

  /** Only `ensures result` itself marks a predicate. `ensures result == (x > 0)` describes a function and is left alone. */
  private def assertsResult(clauses: List[AST.Expression]): Boolean =
    clauses.exists { case AST.Id(_, "result") => true; case _ => false }

  private def isBoolean(t: AST.TypeNode): Boolean = t != null && t.desc == AST.PrimitiveType(AST.KBoolean)

  private def candidatesOf(unit: AST.CompilationUnit): Seq[Candidate] =
    unit.toplevels.flatMap {
      case f: AST.FunctionDeclaration if isBoolean(f.returnType) && assertsResult(f.ensures) =>
        Seq(Candidate(Some(topClassOf(unit)), f.name, f.args.size, unit.sourceFile, f.location,
          topLevel = true, topLevelState = unit.toplevels.exists(t => t.isInstanceOf[AST.GlobalVariableDeclaration] || t.isInstanceOf[AST.LocalVariableDeclaration])))
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
  private def runOne(c: Candidate, classes: Seq[CompiledClass], loader: ClassLoader): Option[String] = {
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
      if (bad >= 0) Some(s"not checkable: it cannot sample a parameter of type ${types(bad).getSimpleName}")
      else combos(perParam).iterator.map(args => call(m, args)).collectFirst { case Some(why) => why }
        .map { why =>
          if (c.topLevelState && why.startsWith("returned false"))
            "not checkable: it may read a top-level val or var, which the build has not set (a script sets them when it starts). Put the predicate in a class or object"
          else why
        }
    }
  }

  /** `None` when the call returns true; otherwise why not, with the arguments and how to reproduce them. */
  private def call(m: Method, args: Array[AnyRef]): Option[String] = {
    val shown = if (args.isEmpty) "" else args.map(String.valueOf).mkString(" for (", ", ", ")") +
      s" [--contract-seed ${config.contractSeed} --contract-samples ${config.contractSamples}]"
    try {
      if (m.invoke(null, args*).asInstanceOf[java.lang.Boolean]) None else Some("returned false" + shown)
    } catch {
      case e: java.lang.reflect.InvocationTargetException =>
        val cause = Option(e.getCause).getOrElse(e)
        Some(s"threw ${cause.getClass.getSimpleName}: ${cause.getMessage}$shown")
      case e: Throwable => Some(s"threw ${e.getClass.getSimpleName}$shown")
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
