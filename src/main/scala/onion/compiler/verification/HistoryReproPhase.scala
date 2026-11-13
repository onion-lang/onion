package onion.compiler.verification

import onion.compiler.{CompileError, CompileWarning, CompiledClass, CompilerConfig, ContractMode, Location, OnionClassLoader, SemanticError, WarningCategory, WarningLevel}
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
    if (config.contracts != ContractMode.Check || !config.runContracts) return classes
    val units = ctx.debugArtifacts.rewrittenUnits.getOrElse(return classes)
    // The classes this module is built against: their `@onion.Contract` is how a contract crosses a
    // module boundary (RFC #2557). A type this compilation defines itself is read from its own AST.
    val table = new onion.compiler.ClassTable(config.classPath.mkString(java.io.File.pathSeparator), Some(onion.compiler.ClassTable.shared))
    val bindings = ctx.debugArtifacts.typedBindings.getOrElse(Map.empty)
    val analysis = new HistoryWritePass(null, name => table.load(name), bindings.get)
    val repros = analysis.counterexamples(units)
    // A proved history prints nothing by default; --verbose lists what the solver proved.
    if (config.verbose) analysis.proved.foreach(p => System.err.println(s"[history] proved: $p"))
    // What nothing settled is said, never left to look like a proof (#2574).
    ctx.addWarnings(shown(analysis.unchecked.toSeq.map(notChecked)))
    if (repros.isEmpty) return classes

    val loader = new OnionClassLoader(classOf[OnionClassLoader].getClassLoader, config.classPath, classes)
    val thread = Thread.currentThread
    val previous = thread.getContextClassLoader
    thread.setContextClassLoader(loader)
    val errors = ArrayBuffer.empty[CompileError]
    val warnings = ArrayBuffer.empty[CompileWarning]
    try {
      val build = new BuildRun(config, ctx.debugArtifacts.typedClasses.getOrElse(Nil))
      for (r <- repros) {
        val location = r.method.location
        // A replay runs the constructor and the method; it runs only when neither reaches the
        // outside world, and only for as long as the build waits (#2601). Otherwise it stays W0021.
        // A replay that is not run, or not waited for, also gets W0023 saying why (panel block on #2610).
        val effects = build.constructorEffects(r.cls.name) ++
          build.methodEffects(Some(r.cls.name), r.method.name, r.method.args.size, static = false)
        def notRun(message: String): Unit =
          warnings += CompileWarning(r.sourceFile, location, WarningCategory.PredicateNotRun, message)
        val replayed =
          if (effects.nonEmpty) { notRun(BuildRun.replayNotRunMessage(r.method.name, r.history, effects)); false }
          else build.timed(loader)(reproduce(r, loader)) match {
            case None =>
              notRun(BuildRun.replayTimeoutMessage(r.method.name, r.history, config.contractTimeoutMillis)); false
            case Some(result) => result.contains(true)
          }
        if (replayed) {
          errors += CompileError(r.sourceFile, location,
            Message("error.semantic.historyReproduced", Array[Any](r.method.name, r.history, r.shown)),
            Some(SemanticError.HISTORY_REPRODUCED.errorCode))
        } else {
          warnings += CompileWarning(r.sourceFile, location, WarningCategory.HistoryNotProved,
            Message("warning.historyNotProved", Array[Any](r.method.name, r.history, r.shown)))
        }
      }
    } finally thread.setContextClassLoader(previous)
    ctx.addWarnings(shown(warnings.toSeq))
    if (errors.nonEmpty) throw new CompilationException(errors.toSeq)
    classes
  }

  /** This phase runs after typing's warning filter, so it applies `--warn off` and `--Wno` itself. */
  private def shown(ws: Seq[CompileWarning]): Seq[CompileWarning] =
    if (config.warningLevel == WarningLevel.Off) Nil
    else ws.filterNot(w => config.suppressedWarnings.contains(w.category))

  private def notChecked(u: HistoryWritePass.Unchecked): CompileWarning = {
    val method = s"${u.cls.name}.${u.method.name}"
    val message = u.history match {
      case Some(h) =>
        val reason = (u.reason, u.fieldType) match {
          // A write the checks cannot follow names its line and why (#2599).
          case (Some(("lambda", _)), _)   => Message("warning.historyNotChecked.reason.lambda", u.line.toString)
          case (Some(("invariant", _)), _) => Message("warning.historyNotChecked.reason.invariant")
          case (Some(("inherited", sub)), _) => Message("warning.historyNotChecked.reason.inherited", sub, Option(u.via).getOrElse(u.method.name))
          case (Some(("super", from)), _) => Message("warning.historyNotChecked.reason.super", Option(u.via).getOrElse(u.method.name), from)
          case (_, Some(t))               => Message("warning.historyNotChecked.reason.type", u.field, t)
          case _                          => Message("warning.historyNotChecked.reason.body")
        }
        Message("warning.historyNotChecked", Array[Any](method, u.field, h, reason))
      case None => Message("warning.historyNotChecked.form", Array[Any](method, u.field, u.line.toString))
    }
    CompileWarning(u.sourceFile, Option(u.at).getOrElse(u.method.location), WarningCategory.HistoryNotChecked, message)
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
