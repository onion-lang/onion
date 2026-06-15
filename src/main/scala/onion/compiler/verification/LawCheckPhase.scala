package onion.compiler.verification

import onion.compiler.{CompiledClass, CompileError, CompilerConfig, OnionClassLoader, SemanticError}
import onion.compiler.exceptions.CompilationException
import onion.compiler.pipeline.{CompilerPhase, PhaseContext}

import java.lang.reflect.{InvocationTargetException, Method, Modifier}
import scala.collection.mutable.ArrayBuffer

/**
 * B3: run `law` / `example` clauses at compile time and turn failures into compile errors.
 *
 * By this phase records and their synthesized methods are already bytecode, so we load them
 * and invoke the boolean check methods discovered by naming convention:
 *   onion$$example$$<id>()         — concrete check; must return true
 *   onion$$law$$<name>(p: T, ...)  — property; checked over generated argument samples
 *
 * A false result or a thrown exception becomes a CompileError (E0065 example / E0064 law),
 * collected and raised as a CompilationException (PipelineRunner turns it into diagnostics).
 * Laws whose parameter types aren't generatable are skipped (MVP). The whole phase is gated
 * on CompilerConfig.checkLaws.
 */
final class LawCheckPhase(config: CompilerConfig)
    extends CompilerPhase[Seq[CompiledClass], Seq[CompiledClass]] {

  private val N = 40
  private val ExamplePrefix = "onion$$example$$"
  private val LawPrefix = "onion$$law$$"

  def name: String = "LawCheck"

  def run(classes: Seq[CompiledClass], ctx: PhaseContext): Seq[CompiledClass] = {
    if (!config.checkLaws) return classes
    // Same parent CL as Shell.run so synthesized methods resolve the onion stdlib.
    val loader = new OnionClassLoader(classOf[OnionClassLoader].getClassLoader, config.classPath, classes)
    val errors = ArrayBuffer.empty[CompileError]
    val thread = Thread.currentThread
    val previous = thread.getContextClassLoader
    thread.setContextClassLoader(loader)
    try {
      for (cc <- classes) {
        // initialize=false: don't run static initializers (top-level side effects) just to
        // scan for check methods; invoking a check lazily initializes only that record class.
        val clazz = try Class.forName(cc.className, false, loader) catch { case _: Throwable => null }
        if (clazz != null) {
          for (m <- clazz.getDeclaredMethods if isBooleanStatic(m)) {
            val nm = m.getName
            if (nm.startsWith(ExamplePrefix)) runExample(cc, m, errors)
            else if (nm.startsWith(LawPrefix)) runLaw(cc, m, loader, errors)
          }
        }
      }
    } finally {
      thread.setContextClassLoader(previous)
    }
    if (errors.nonEmpty) throw new CompilationException(errors.toSeq)
    classes
  }

  private def isBooleanStatic(m: Method): Boolean =
    Modifier.isStatic(m.getModifiers) && m.getReturnType == java.lang.Boolean.TYPE

  private def runExample(cc: CompiledClass, m: Method, errors: ArrayBuffer[CompileError]): Unit = {
    if (m.getParameterCount != 0) return
    m.setAccessible(true)
    val label = m.getName.substring(ExamplePrefix.length)
    try {
      if (m.invoke(null) != java.lang.Boolean.TRUE)
        errors += err(SemanticError.EXAMPLE_FAILED.errorCode, cc, s"example '$label' in ${simpleName(cc)} failed: evaluated to false")
    } catch {
      case e: Throwable =>
        errors += err(SemanticError.EXAMPLE_FAILED.errorCode, cc, s"example '$label' in ${simpleName(cc)} failed: threw ${describe(rootCause(e))}")
    }
  }

  private def runLaw(cc: CompiledClass, m: Method, loader: ClassLoader, errors: ArrayBuffer[CompileError]): Unit = {
    m.setAccessible(true)
    val label = m.getName.substring(LawPrefix.length)
    val paramTypes = m.getParameterTypes
    val perParam: Array[List[AnyRef]] = paramTypes.map(t => ArgGenerator.generateValues(t, loader).orNull)
    if (perParam.exists(_ == null)) return // a parameter type isn't generatable — skip this law (MVP)
    val combos: List[Array[AnyRef]] = if (paramTypes.isEmpty) List(Array.empty[AnyRef]) else argCombos(perParam)
    var done = false
    val it = combos.iterator
    while (it.hasNext && !done) {
      val args = it.next()
      val shown = if (args.isEmpty) "(no args)" else args.map(String.valueOf).mkString(", ")
      try {
        if (m.invoke(null, args*) != java.lang.Boolean.TRUE) {
          errors += err(SemanticError.LAW_VIOLATION.errorCode, cc, s"law '$label' in ${simpleName(cc)} falsified by counterexample: ($shown)")
          done = true
        }
      } catch {
        case e: Throwable =>
          errors += err(SemanticError.LAW_VIOLATION.errorCode, cc, s"law '$label' in ${simpleName(cc)} threw on ($shown): ${describe(rootCause(e))}")
          done = true
      }
    }
  }

  /** Diagonal zip (up to the shortest list) plus a one-at-a-time boundary sweep, capped at N. */
  private def argCombos(perParam: Array[List[AnyRef]]): List[Array[AnyRef]] = {
    val arity = perParam.length
    val minLen = perParam.map(_.length).min
    val diag = (0 until minLen).map(i => Array.tabulate(arity)(p => perParam(p)(i))).toList
    val sweep = (0 until arity).flatMap { p =>
      perParam(p).indices.map { k =>
        Array.tabulate(arity)(q => if (q == p) perParam(q)(k) else perParam(q)(0))
      }
    }.toList
    (diag ++ sweep).take(N)
  }

  private def err(code: String, cc: CompiledClass, msg: String): CompileError =
    CompileError(cc.className, null, msg, Some(code))

  private def simpleName(cc: CompiledClass): String = {
    val n = cc.className
    val i = n.lastIndexOf('.')
    if (i >= 0) n.substring(i + 1) else n
  }

  private def rootCause(t: Throwable): Throwable = t match {
    case ite: InvocationTargetException if ite.getCause != null => ite.getCause
    case _ => t
  }

  private def describe(t: Throwable): String = {
    val msg = Option(t.getMessage).filter(_.nonEmpty).map(": " + _).getOrElse("")
    t.getClass.getSimpleName + msg
  }
}
