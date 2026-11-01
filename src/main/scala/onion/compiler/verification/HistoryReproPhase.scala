package onion.compiler.verification

import onion.compiler.{CompileError, CompileWarning, CompiledClass, CompilerConfig, ContractMode, Location, OnionClassLoader, SemanticError, WarningCategory}
import onion.compiler.exceptions.CompilationException
import onion.compiler.pipeline.{CompilerPhase, PhaseContext}
import onion.compiler.toolbox.Message
import onion.compiler.typing.HistoryWritePass
import onion.compiler.typing.HistoryWritePass.Repro

import java.lang.reflect.{Constructor, Field, Method}
import scala.collection.mutable.ArrayBuffer

/**
 * Confirms the solver's history counterexamples by running the code (RFC #2557, design 7.4).
 *
 * For each counterexample the phase builds an instance of the compiled class, sets the fields to
 * the values the solver found, calls the method, and checks the history on the values before and
 * after. A history that really fails is `E0112`: the contract, as written, is wrong, and the
 * message says what to run to see it. One that does not fail on the compiled code, or that cannot
 * be run (no no-argument constructor, a field it cannot set, the call throws), stays the warning
 * `W0021`, since the solver's model may not match what the program does.
 *
 * Only runs with `--contracts check`, and only for classes that have a `history`.
 */
final class HistoryReproPhase(config: CompilerConfig) extends CompilerPhase[Seq[CompiledClass], Seq[CompiledClass]] {
  def name: String = "HistoryRepro"

  def run(classes: Seq[CompiledClass], ctx: PhaseContext): Seq[CompiledClass] = {
    if (config.contracts != ContractMode.Check) return classes
    val units = ctx.debugArtifacts.rewrittenUnits.getOrElse(return classes)
    val analysis = new HistoryWritePass(null)
    val repros = analysis.counterexamples(units)
    // A proved history prints nothing by default; --verbose lists what the solver proved.
    if (config.verbose) analysis.proved.foreach(p => System.err.println(s"[history] proved: $p"))
    if (repros.isEmpty) return classes

    val loader = new OnionClassLoader(classOf[OnionClassLoader].getClassLoader, config.classPath, classes)
    val thread = Thread.currentThread
    val previous = thread.getContextClassLoader
    thread.setContextClassLoader(loader)
    val errors = ArrayBuffer.empty[CompileError]
    val warnings = ArrayBuffer.empty[CompileWarning]
    try {
      for (r <- repros) {
        val location = r.method.location
        if (reproduce(r, loader)) {
          errors += CompileError(r.sourceFile, location,
            Message("error.semantic.historyReproduced", Array[Any](r.method.name, r.history, r.shown)),
            Some(SemanticError.HISTORY_REPRODUCED.errorCode))
        } else {
          warnings += CompileWarning(r.sourceFile, location, WarningCategory.HistoryNotProved,
            s"'${r.method.name}' can break the history '${r.history}': for example when ${r.shown}. " +
              "Add an invariant or a requires that rules this out, or weaken the history")
        }
      }
    } finally thread.setContextClassLoader(previous)
    ctx.addWarnings(warnings.toSeq)
    if (errors.nonEmpty) throw new CompilationException(errors.toSeq)
    classes
  }

  /** Whether running the method from the solver's state breaks the history. False when it cannot be run. */
  private def reproduce(r: Repro, loader: ClassLoader): Boolean =
    try {
      val clazz = Class.forName(r.cls.name, true, loader)
      val ctor: Constructor[?] = clazz.getDeclaredConstructor()
      ctor.setAccessible(true)
      val obj = ctor.newInstance().asInstanceOf[AnyRef]
      for ((name, value) <- r.before if findField(clazz, name).isDefined && !r.method.args.exists(_.name == name))
        findField(clazz, name).get.setInt(obj, value)
      val method = findMethod(clazz, r.method.name, r.method.args.size).getOrElse(return false)
      val args = r.method.args.map(a => Int.box(r.before.getOrElse(a.name, 0)).asInstanceOf[AnyRef])
      val field = findField(clazz, r.field).getOrElse(return false)
      val before = field.getInt(obj)
      method.invoke(obj, args*)
      val after = field.getInt(obj)
      r.op match {
        case ">=" => after < before
        case "<=" => after > before
        case _    => after != before
      }
    } catch { case _: Throwable => false }

  private def findField(c: Class[?], name: String): Option[Field] = {
    var k: Class[?] = c
    while (k != null) {
      try { val f = k.getDeclaredField(name); f.setAccessible(true); return Some(f) }
      catch { case _: NoSuchFieldException => k = k.getSuperclass }
    }
    None
  }

  private def findMethod(c: Class[?], name: String, arity: Int): Option[Method] = {
    var k: Class[?] = c
    while (k != null) {
      k.getDeclaredMethods.find(m => m.getName == name && m.getParameterCount == arity) match {
        case Some(m) => m.setAccessible(true); return Some(m)
        case None => k = k.getSuperclass
      }
    }
    None
  }
}
