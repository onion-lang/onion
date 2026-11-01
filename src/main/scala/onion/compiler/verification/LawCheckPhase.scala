package onion.compiler.verification

import onion.compiler.{CompiledClass, CompileError, CompilerConfig, Location, OnionClassLoader, SemanticError}
import onion.compiler.exceptions.CompilationException
import onion.compiler.pipeline.{CompilerPhase, PhaseContext}
import onion.compiler.toolbox.Message

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
 * A law whose parameter types aren't generatable is reported as E0074, and a class that
 * declares checks but fails to load as E0075, rather than either being skipped:
 * a check that silently does not run is indistinguishable from one that passed, and so is
 * worse than no check at all. The whole phase is gated on CompilerConfig.checkLaws.
 */
final class LawCheckPhase(config: CompilerConfig)
    extends CompilerPhase[Seq[CompiledClass], Seq[CompiledClass]] {

  private val N = config.lawSamples
  private val ExamplePrefix = "onion$$example$$"
  private val LawPrefix = "onion$$law$$"

  def name: String = "LawCheck"

  def run(classes: Seq[CompiledClass], ctx: PhaseContext): Seq[CompiledClass] = {
    if (!config.checkLaws) return classes
    // Most programs declare no law or example. Reading the check index first (a cheap scan
    // of the class bytes) avoids building a class loader and defining every generated class
    // just to discover there is nothing to run -- which was ~15% of a warm compilation.
    val indexes = classes.collect { case cc if CheckMethodIndex.mayHaveChecks(cc.content) => cc -> CheckMethodIndex.read(cc.content) }
    if (!indexes.exists(_._2.hasChecks)) return classes
    // Same parent CL as Shell.run so synthesized methods resolve the onion stdlib.
    val loader = new OnionClassLoader(classOf[OnionClassLoader].getClassLoader, config.classPath, classes)
    val errors = ArrayBuffer.empty[CompileError]
    clauseLocations = clauseLocationsOf(ctx)
    shownRecords = shownRecordsOf(ctx)
    val thread = Thread.currentThread
    val previous = thread.getContextClassLoader
    thread.setContextClassLoader(loader)
    try {
      for ((cc, index) <- indexes) {
        // initialize=false: don't run static initializers (top-level side effects) just to
        // scan for check methods; invoking a check lazily initializes only that record class.
        val clazz = try Class.forName(cc.className, false, loader) catch { case _: Throwable => null }
        if (clazz != null) {
          for (m <- clazz.getDeclaredMethods if isBooleanStatic(m)) {
            val nm = m.getName
            if (nm.startsWith(ExamplePrefix)) runExample(cc, index, m, errors)
            else if (nm.startsWith(LawPrefix)) runLaw(cc, index, m, loader, errors)
          }
        } else if (index.hasChecks) {
          // Swallowing this used to take every law in the class with it, silently.
          // Reading the bytes tells us the class was carrying checks, so the failure
          // is worth reporting rather than treating as "nothing to do".
          errors += err(SemanticError.LAW_CLASS_NOT_LOADABLE.errorCode, cc, index, None,
            Message("error.semantic.lawClassNotLoadable", simpleName(cc)))
        }
      }
    } finally {
      thread.setContextClassLoader(previous)
    }
    if (errors.nonEmpty) throw new CompilationException(errors.toSeq)
    classes
  }

  // The class file records a clause's line but not its column. The parsed units still
  // know where each `law`/`example` clause starts, so each check method maps back to its
  // clause: keyed by source file, owning record (None for a top-level example) and the
  // synthesized method name, never by line alone. A line-only key took the column from
  // another file's clause on the same line, and from the first clause of a one-line body
  // (#2540).
  private type ClauseKey = (String, Option[String], String)
  private var clauseLocations: Map[ClauseKey, Location] = Map.empty

  private def clauseLocationsOf(ctx: PhaseContext): Map[ClauseKey, Location] = {
    // Mirrors the naming in Rewriting: `onion$$law$$<name>`, and `onion$$example$$<name>`
    // or the example's index (within its record; across all files for top-level ones).
    var topLevelExamples = 0
    val entries = ctx.debugArtifacts.parsedUnits.toList.flatten.flatMap { unit =>
      val file = Option(unit.sourceFile).getOrElse("")
      unit.toplevels.flatMap {
        case r: onion.compiler.AST.RecordDeclaration =>
          r.laws.map(l => (file, Some(r.name), LawPrefix + l.name) -> l.location) ++
            r.examples.zipWithIndex.map { case (ex, i) =>
              (file, Some(r.name), ExamplePrefix + ex.name.getOrElse(i.toString)) -> ex.location
            }
        case ex: onion.compiler.AST.TopLevelExample =>
          val suffix = ex.name.getOrElse(topLevelExamples.toString)
          topLevelExamples += 1
          List((file, None, ExamplePrefix + suffix) -> ex.location)
        case _ => Nil
      }
    }
    entries.filter { case (_, l) => l != null && l.line > 0 && l.column > 0 }.toMap
  }

  private def baseName(path: String): String =
    path.substring(math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1)

  /** The clause's column, if its parsed location is known and lies on the recorded line. */
  private def clauseColumn(file: String, owner: String, method: String, line: Int): Option[Int] = {
    def lookup(matches: String => Boolean): Option[Location] =
      List(Some(owner), None).iterator.flatMap { o =>
        clauseLocations.collectFirst { case ((f, `o`, `method`), l) if matches(f) => l }
      }.nextOption()
    lookup(_ == file).orElse(lookup(f => baseName(f) == baseName(file)))
      .filter(_.line == line).map(_.column)
  }

  // Records whose `toString` is the synthesized one, with their component names: a
  // counterexample shows them component by component (CounterexampleText).
  private var shownRecords: Map[String, Seq[String]] = Map.empty

  private def shownRecordsOf(ctx: PhaseContext): Map[String, Seq[String]] =
    ctx.debugArtifacts.typedClasses.toList.flatten.flatMap { cd =>
      val synthesizedToString = cd.methods("toString").exists(m =>
        m.arguments.isEmpty && onion.compiler.Modifier.isSyntheticRecord(m.modifier))
      cd.recordComponents.filter(_ => synthesizedToString).map(cs => cd.name -> cs.map(_._1).toSeq)
    }.toMap

  private def isBooleanStatic(m: Method): Boolean =
    Modifier.isStatic(m.getModifiers) && m.getReturnType == java.lang.Boolean.TYPE

  private def runExample(cc: CompiledClass, index: CheckMethodIndex, m: Method, errors: ArrayBuffer[CompileError]): Unit = {
    if (m.getParameterCount != 0) return
    m.setAccessible(true)
    val label = m.getName.substring(ExamplePrefix.length)
    try {
      if (m.invoke(null) != java.lang.Boolean.TRUE)
        errors += err(SemanticError.EXAMPLE_FAILED.errorCode, cc, index, Some(m.getName),
          Message("error.semantic.exampleFailedFalse", Array[Any](label, simpleName(cc))))
    } catch {
      case e: Throwable =>
        errors += err(SemanticError.EXAMPLE_FAILED.errorCode, cc, index, Some(m.getName),
          Message("error.semantic.exampleFailedThrew", Array[Any](label, simpleName(cc), describe(rootCause(e)))))
    }
  }

  private def runLaw(cc: CompiledClass, index: CheckMethodIndex, m: Method, loader: ClassLoader, errors: ArrayBuffer[CompileError]): Unit = {
    m.setAccessible(true)
    val label = m.getName.substring(LawPrefix.length)
    val paramTypes = m.getParameterTypes
    val perParam: Array[List[AnyRef]] =
      paramTypes.map(t => ArgGenerator.generateValues(t, loader, config.lawSamples, config.lawSeed).orNull)
    // A law we cannot generate arguments for must not pass silently: skipping it made an
    // unrunnable law indistinguishable from one that held, which is worse than no law at
    // all because it produces unearned confidence (issue #346).
    val ungeneratable = perParam.indexWhere(_ == null)
    if (ungeneratable >= 0) {
      errors += err(SemanticError.LAW_PARAMETER_NOT_GENERATABLE.errorCode, cc, index, Some(m.getName),
        Message("error.semantic.lawParameterNotGeneratable",
          Array[Any](label, simpleName(cc), typeName(paramTypes(ungeneratable)))))
      return
    }
    val combos: List[Array[AnyRef]] = if (paramTypes.isEmpty) List(Array.empty[AnyRef]) else argCombos(perParam)
    var done = false
    val it = combos.iterator
    while (it.hasNext && !done) {
      val args = it.next()
      val shown = if (args.isEmpty) "(no args)" else args.map(CounterexampleText.render(_, shownRecords)).mkString(", ")
      try {
        if (m.invoke(null, args*) != java.lang.Boolean.TRUE) {
          errors += err(SemanticError.LAW_VIOLATION.errorCode, cc, index, Some(m.getName),
            Message("error.semantic.lawFalsified", Array[Any](label, simpleName(cc), shown, seedNote)))
          done = true
        }
      } catch {
        case e: Throwable =>
          errors += err(SemanticError.LAW_VIOLATION.errorCode, cc, index, Some(m.getName),
            Message("error.semantic.lawThrew", Array[Any](label, simpleName(cc), shown, describe(rootCause(e)), seedNote)))
          done = true
      }
    }
  }

  /**
   * How to reproduce -- and how to widen -- the run that produced a counterexample.
   * Without it the sample set is an unnamed constant and a failure cannot be re-derived.
   */
  private def seedNote: String =
    s"--law-seed ${config.lawSeed} --law-samples ${config.lawSamples}"

  /** A reader-facing name for a parameter type (`int[]`, `Multi`, `Shape`). */
  private def typeName(t: Class[?]): String = {
    val n = t.getSimpleName
    if (n.nonEmpty) n else t.getName
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

  /**
   * The clause's position, recovered from the class file. Before this the sourceFile slot
   * held the *class* name and the location was null, so a law failure rendered with no
   * file, no line and no caret, and landed at line 0 in the editor.
   */
  private def err(
    code: String, cc: CompiledClass, index: CheckMethodIndex,
    methodName: Option[String], msg: String
  ): CompileError = {
    val file = index.sourceFile.getOrElse(cc.className)
    val location = methodName.flatMap { m =>
      index.lineOf(m).map(line => new Location(line, clauseColumn(file, simpleName(cc), m, line).getOrElse(1)))
    }.orNull
    CompileError(file, location, msg, Some(code))
  }

  private def simpleName(cc: CompiledClass): String = {
    val n = cc.className
    val i = n.lastIndexOf('.')
    if (i >= 0) n.substring(i + 1) else n
  }

  private def rootCause(t: Throwable): Throwable = t match {
    case ite: InvocationTargetException if ite.getCause != null => ite.getCause
    case _ => t
  }

  // On one line: the message may quote the input, newline and all (`For input string: "\n"`).
  private def describe(t: Throwable): String = {
    val msg = Option(t.getMessage).filter(_.nonEmpty).map(": " + _).getOrElse("")
    CounterexampleText.oneLine(t.getClass.getSimpleName + msg)
  }
}
