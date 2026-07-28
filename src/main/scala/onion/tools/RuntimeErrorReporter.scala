/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.tools

import java.io.PrintStream
import onion.compiler.toolbox.Message

/**
 * Formats an uncaught exception from a running script for a user who did not
 * write the JVM plumbing around their code (see issue #450): the raw trace
 * mixes the script's own frames with compiler-synthesized wrapper methods
 * (`EntryPointSupport.createStartMethod`/`createMain`) and the reflective
 * launcher (`Shell.run` invokes the compiled `main` via `Method.invoke`).
 */
object RuntimeErrorReporter {
  /** Hardcoded synthetic wrapper method name every compiled script gets
   *  (see `EntryPointSupport.createStartMethod`) — never user-authored. */
  private val SyntheticStartMethod = "start"

  /**
   * The leading run of frames belonging to the script's own source file,
   * with the compiler's synthetic wrapper frames removed: the `start`
   * dispatcher (always synthetic) and any frame with no line number (the
   * synthetic static JVM-entry `main` built by `EntryPointSupport.createMain`
   * has a null `Location`, so it never gets one). Stops at the first frame
   * from a different file — that's the reflective launcher and everything
   * below it, none of which the user wrote.
   */
  private[tools] def relevantFrames(error: Throwable, scriptFileName: String): Seq[StackTraceElement] =
    error.getStackTrace.toIndexedSeq
      .takeWhile(f => f.getFileName != null && f.getFileName == scriptFileName)
      .filterNot(f => f.getMethodName == SyntheticStartMethod || f.getLineNumber <= 0)

  private def friendlyName(error: Throwable): String = error match {
    case a: ArithmeticException if Option(a.getMessage).exists(_.contains("by zero")) =>
      Message("runtime.divisionByZero")
    case _: ArrayIndexOutOfBoundsException | _: StringIndexOutOfBoundsException | _: IndexOutOfBoundsException =>
      Message("runtime.indexOutOfRange")
    case _: NullPointerException =>
      Message("runtime.nullReference")
    case _: ClassCastException =>
      Message("runtime.invalidCast")
    case _: NegativeArraySizeException =>
      Message("runtime.negativeArraySize")
    case _: NumberFormatException =>
      Message("runtime.invalidNumberFormat")
    case other =>
      Message("runtime.generic", other.getClass.getSimpleName)
  }

  /** Renders a short, script-relative report: a friendly one-line header
   *  naming the failure the way the language's own diagnostics would,
   *  the script's own stack frames (no compiler/JDK noise), and a hint
   *  for recovering the full Java trace. */
  def render(error: Throwable, scriptFileName: String): String = {
    val nl = System.lineSeparator()
    val hint = s"  ${Message("runtime.stacktraceHint")}"
    if (error.isInstanceOf[StackOverflowError]) {
      Seq(s"error: ${Message("runtime.stackOverflow")}", hint).mkString(nl)
    } else {
      val msg = Option(error.getMessage).filter(_.nonEmpty)
      val header = msg.map(m => s"${friendlyName(error)}: $m").getOrElse(friendlyName(error))
      val frameLines = relevantFrames(error, scriptFileName).map(f => s"  at ${f.getFileName}:${f.getLineNumber}")
      (Seq(s"error: $header") ++ frameLines ++ Seq(hint)).mkString(nl)
    }
  }

  def report(error: Throwable, scriptFileName: String, out: PrintStream = System.err): Unit =
    out.println(render(error, scriptFileName))
}
