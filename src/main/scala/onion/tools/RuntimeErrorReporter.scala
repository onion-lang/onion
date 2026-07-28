package onion.tools

import onion.compiler.toolbox.Message

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
  private def friendlyName(t: Throwable): Option[String] = (t match {
    case _: ArithmeticException if isDivideByZero(t) => Some("runtime.divisionByZero")
    case _: ArrayIndexOutOfBoundsException           => Some("runtime.arrayIndexOutOfRange")
    case _: StringIndexOutOfBoundsException          => Some("runtime.stringIndexOutOfRange")
    case _: NegativeArraySizeException               => Some("runtime.negativeArraySize")
    case _: IndexOutOfBoundsException                => Some("runtime.indexOutOfRange")
    case _: NullPointerException                     => Some("runtime.nullReference")
    case _: ClassCastException                       => Some("runtime.invalidCast")
    case _: NumberFormatException                    => Some("runtime.invalidNumberFormat")
    case _: StackOverflowError                       => Some("runtime.stackOverflow")
    case _: OutOfMemoryError                         => Some("runtime.outOfMemory")
    case _                                           => None
  }).map(Message.apply)

  private def isDivideByZero(t: Throwable): Boolean =
    Option(t.getMessage).exists(_.contains("zero"))

  /**
   * Whether the JVM's message would only restate the headline. `/ by zero` after
   * "division by zero" is the clearest case; an index message ("Index 5 out of bounds
   * for length 2") genuinely adds the numbers, so it is kept.
   */
  private def messageIsRedundant(t: Throwable): Boolean = t match {
    case _: ArithmeticException if isDivideByZero(t) => true
    case _: StackOverflowError                       => true
    case _: OutOfMemoryError                         => true
    case _                                           => false
  }

  /** Extra context worth saying once, where the plain message leaves a user stuck. */
  private def note(t: Throwable): Option[String] = (t match {
    case _: StackOverflowError   => Some("runtime.stackOverflowNote")
    case _: NullPointerException => Some("runtime.nullReferenceNote")
    case _                       => None
  }).map(Message.apply)

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
    // Only add the JVM's own message when it carries information the headline does not.
    // Comparing the two strings cannot work — the headline is localized while the JVM
    // message is always English ("ゼロ除算: / by zero"). Decide per exception type
    // instead: for these, the class alone says everything and the message is noise.
    if (!messageIsRedundant(t)) message.foreach(m => sb.append(s": $m"))
    sb.append('\n')

    // The frames below the first are the call path that reached it; a stack overflow
    // repeats them endlessly, so cap it and say so.
    val rest = frames.drop(1)
    val shown = rest.take(10)
    shown.foreach { f =>
      sb.append("  " + Message("runtime.calledFrom",
        Array[Any](f.getFileName, Integer.valueOf(f.getLineNumber), f.getMethodName)) + "\n")
    }
    if (rest.length > shown.length) {
      sb.append("  " + Message("runtime.moreFrames",
        Integer.valueOf(rest.length - shown.length)) + "\n")
    }

    note(t).foreach(n => sb.append(s"  note: $n\n"))
    if (frames.isEmpty) {
      sb.append("  note: " + Message("runtime.noUserFrames") + "\n")
    } else {
      sb.append("  " + Message("runtime.stacktraceHint") + "\n")
    }
    sb.toString
  }

  private def simpleName(t: Throwable): String = {
    val n = t.getClass.getName
    val short = n.substring(n.lastIndexOf('.') + 1).replace('$', '.')
    short
  }
}
