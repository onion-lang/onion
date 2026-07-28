package onion.tools

/**
 * Presents an uncaught runtime error the way the compiler presents a diagnostic
 * (issue #450).
 *
 * Letting the exception reach the JVM's default handler printed frames the user never
 * wrote and cannot act on — the synthesized `start`/`main` wrappers, the reflective
 * launcher, and for a `StackOverflowError` thousands of identical lines. Compile-time
 * diagnostics were polished repeatedly; this is the same information rendered with the
 * same care.
 *
 * Nothing here changes what is thrown or the exit code: it is presentation only, and
 * `--stacktrace` restores the untouched trace for when the JVM detail is the point.
 */
object RuntimeErrorReporter {

  private val hiddenPrefixes = Seq(
    "java.base/", "java.", "jdk.", "sun.", "scala.",
    "onion.tools.", "onion.compiler."
  )

  /**
   * A short, human-readable name for the failure, or `None` when the exception has no
   * better name than its own. Kept deliberately small: a wrong friendly name is worse
   * than a class name the user can search for.
   */
  private def friendlyName(t: Throwable): Option[String] = t match {
    case _: ArithmeticException if isDivideByZero(t) => Some("division by zero")
    case _: ArrayIndexOutOfBoundsException           => Some("array index out of range")
    case _: StringIndexOutOfBoundsException          => Some("string index out of range")
    case _: IndexOutOfBoundsException                => Some("index out of range")
    case _: NullPointerException                     => Some("null value used")
    case _: ClassCastException                       => Some("invalid cast")
    case _: NumberFormatException                    => Some("malformed number")
    case _: StackOverflowError                       => Some("stack overflow")
    case _: OutOfMemoryError                         => Some("out of memory")
    case _                                           => None
  }

  private def isDivideByZero(t: Throwable): Boolean =
    Option(t.getMessage).exists(_.contains("zero"))

  /** Extra context worth saying once, where the plain message leaves a user stuck. */
  private def note(t: Throwable): Option[String] = t match {
    case _: StackOverflowError =>
      Some("this usually means recursion that does not terminate, or recursion too " +
           "deep to run on the JVM stack. Tail-call optimization applies to direct " +
           "and mutual self-recursion only.")
    case _: NullPointerException =>
      Some("a nullable value (`T?`) was used without a null check, or a Java method " +
           "returned null where a value was expected.")
    case _ => None
  }

  /**
   * Whether a stack frame is one the user's own source produced.
   *
   * A script compiles to `<name>Main` with a synthesized `start` wrapper and a
   * `main(String[])` entry point around the user's top level, so those frames repeat
   * the same file with either no line number or the line of the top-level statement
   * that started everything. Drop the ones carrying no position; keep the rest, since
   * for a script whose body IS `main` the useful line lives there.
   */
  private def isUserFrame(f: StackTraceElement): Boolean = {
    val cls = f.getClassName
    if (hiddenPrefixes.exists(cls.startsWith)) return false
    if (f.getFileName == null || !f.getFileName.endsWith(".on")) return false
    if (f.getLineNumber <= 0) return false
    // `start` is purely a wrapper; its line points at the top of the file.
    !(cls.endsWith("Main") && f.getMethodName == "start")
  }

  /**
   * Renders `t` for an end user. `scriptName` is used only when no frame carries a
   * position, so the message still says which script failed.
   */
  def render(t: Throwable, scriptName: String): String = {
    val sb = new StringBuilder
    val headline = friendlyName(t) match {
      case Some(name) => name
      case None       => simpleName(t)
    }
    val message = Option(t.getMessage).filter(_.nonEmpty)

    val frames = t.getStackTrace.filter(isUserFrame)
    val where = frames.headOption
      .map(f => s"${f.getFileName}:${f.getLineNumber}")
      .getOrElse(scriptName)

    sb.append(s"$where: error: $headline")
    // Only add the JVM's own message when it says something the headline does not —
    // "division by zero: / by zero" helps nobody.
    message.foreach { m =>
      val normalized = m.toLowerCase.replace("/", "").trim
      val covered = friendlyName(t).exists(n => normalized.split("\\s+").forall(n.contains))
      if (!covered) sb.append(s": $m")
    }
    sb.append('\n')

    // The frames below the first are the call path that reached it; a stack overflow
    // repeats them endlessly, so cap it and say so.
    val rest = frames.drop(1)
    val shown = rest.take(10)
    shown.foreach { f =>
      sb.append(s"  called from ${f.getFileName}:${f.getLineNumber} (${f.getMethodName})\n")
    }
    if (rest.length > shown.length) {
      sb.append(s"  ... and ${rest.length - shown.length} more frames\n")
    }

    note(t).foreach(n => sb.append(s"  note: $n\n"))
    if (frames.isEmpty) {
      sb.append("  note: the failure happened outside the script's own code; " +
                "run with --stacktrace for the full trace.\n")
    } else {
      sb.append("  (run with --stacktrace for the full JVM trace)\n")
    }
    sb.toString
  }

  private def simpleName(t: Throwable): String = {
    val n = t.getClass.getName
    val short = n.substring(n.lastIndexOf('.') + 1).replace('$', '.')
    short
  }
}
