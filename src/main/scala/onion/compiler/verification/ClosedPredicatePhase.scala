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
 * and carries an `ensures` clause, in a class, record or at the top level:
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

  private final case class Candidate(owner: Option[String], name: String, sourceFile: String, location: onion.compiler.Location)

  def run(classes: Seq[CompiledClass], ctx: PhaseContext): Seq[CompiledClass] = {
    if (config.contracts != ContractMode.Check) return classes
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

  private def isBoolean(t: AST.TypeNode): Boolean = t != null && t.desc == AST.PrimitiveType(AST.KBoolean)

  private def candidatesOf(unit: AST.CompilationUnit): Seq[Candidate] =
    unit.toplevels.flatMap {
      case f: AST.FunctionDeclaration if f.args.isEmpty && isBoolean(f.returnType) && f.ensures.nonEmpty =>
        Seq(Candidate(None, f.name, unit.sourceFile, f.location))
      case c: AST.ClassDeclaration =>
        (c.defaultSection.toSeq ++ c.sections).flatMap(_.members).collect {
          case m: AST.MethodDeclaration if qualifies(m) => Candidate(Some(c.name), m.name, unit.sourceFile, m.location)
        }
      case r: AST.RecordDeclaration =>
        r.sections.flatMap(_.members).collect {
          case m: AST.MethodDeclaration if qualifies(m) => Candidate(Some(r.name), m.name, unit.sourceFile, m.location)
        }
      case _ => Nil
    }

  private def qualifies(m: AST.MethodDeclaration): Boolean =
    Modifier.isStatic(m.modifiers) && m.args.isEmpty && isBoolean(m.returnType) && m.ensures.nonEmpty

  /** `None` when the predicate holds (or cannot be found); the reason when it is false or throws. */
  private def runOne(c: Candidate, classes: Seq[CompiledClass], loader: ClassLoader): Option[String] = {
    val owners = c.owner match {
      case Some(o) => classes.filter(cc => cc.className == o || cc.className.endsWith("." + o))
      case None    => classes
    }
    val method: Option[Method] = owners.iterator.flatMap { cc =>
      try Class.forName(cc.className, false, loader).getDeclaredMethods.iterator
          .find(m => m.getName == c.name && m.getParameterCount == 0 && java.lang.reflect.Modifier.isStatic(m.getModifiers) &&
            (m.getReturnType == java.lang.Boolean.TYPE))
      catch { case _: Throwable => None }
    }.nextOption()
    method.flatMap { m =>
      try {
        m.setAccessible(true)
        if (m.invoke(null).asInstanceOf[java.lang.Boolean]) None else Some("false")
      } catch {
        case e: java.lang.reflect.InvocationTargetException =>
          val cause = Option(e.getCause).getOrElse(e)
          Some(s"threw ${cause.getClass.getSimpleName}: ${cause.getMessage}")
        case e: Throwable => Some(s"threw ${e.getClass.getSimpleName}")
      }
    }
  }
}
