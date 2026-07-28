package onion.tools

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class RuntimeErrorReporterSpec extends AnyFunSuite with Matchers:

  /** Build a Throwable whose stack trace mimics what the compiler actually
   *  emits for `def main(): void { ... }` scripts (see EntryPointSupport):
   *  the real frame, a synthetic `start` wrapper (has a line number), a
   *  synthetic static `main` JVM entry (no line number), then JDK/harness
   *  frames that must never reach the user. */
  private def scriptLikeError(scriptFile: String): RuntimeException = {
    val e = new RuntimeException("/ by zero")
    e.setStackTrace(Array(
      new StackTraceElement("aMain", "main", scriptFile, 2),
      new StackTraceElement("aMain", "start", scriptFile, 1),
      new StackTraceElement("aMain", "main", scriptFile, -1),
      new StackTraceElement("java.lang.reflect.Method", "invoke", "Method.java", 580),
      new StackTraceElement("onion.tools.Shell", "run", "Shell.scala", 60),
      new StackTraceElement("onion.tools.ScriptRunner$", "main", "ScriptRunner.scala", 48)
    ))
    e
  }

  test("drops the synthetic start/no-line wrapper frames and everything past the script file") {
    val frames = RuntimeErrorReporter.relevantFrames(scriptLikeError("a.on"), "a.on")
    frames.map(f => (f.getMethodName, f.getLineNumber)) shouldBe Seq(("main", 2))
  }

  test("keeps multiple genuine same-file frames (real user call chain)") {
    val e = new RuntimeException("boom")
    e.setStackTrace(Array(
      new StackTraceElement("aMain", "helper", "deep.on", 5),
      new StackTraceElement("aMain", "main", "deep.on", 2),
      new StackTraceElement("aMain", "start", "deep.on", 1),
      new StackTraceElement("aMain", "main", "deep.on", -1),
      new StackTraceElement("java.lang.reflect.Method", "invoke", "Method.java", 580)
    ))
    val frames = RuntimeErrorReporter.relevantFrames(e, "deep.on")
    frames.map(_.getMethodName) shouldBe Seq("helper", "main")
  }

  test("renders a friendly header for division by zero, naming the language's own vocabulary") {
    val e = new ArithmeticException("/ by zero")
    e.setStackTrace(Array(new StackTraceElement("aMain", "main", "a.on", 2)))
    val rendered = RuntimeErrorReporter.render(e, "a.on")
    rendered should include("division by zero")
    rendered should include("a.on:2")
    rendered should include("--stacktrace")
  }

  test("renders a friendly header for array index out of bounds") {
    val e = new ArrayIndexOutOfBoundsException("Index 3 out of bounds for length 2")
    e.setStackTrace(Array(new StackTraceElement("aMain", "main", "a.on", 4)))
    RuntimeErrorReporter.render(e, "a.on") should include("index out of range")
  }

  test("renders a friendly header for a null reference") {
    val e = new NullPointerException()
    e.setStackTrace(Array(new StackTraceElement("aMain", "main", "a.on", 7)))
    RuntimeErrorReporter.render(e, "a.on") should include("null reference")
  }

  test("StackOverflowError gets a short explanatory message, not thousands of frames") {
    val e = new StackOverflowError()
    e.setStackTrace(Array.fill(2000)(new StackTraceElement("aMain", "recurse", "so.on", 3)))
    val rendered = RuntimeErrorReporter.render(e, "so.on")
    rendered should include("stack overflow")
    rendered should include("recursion")
    rendered.linesIterator.size should be < 10
  }

  test("unknown exception types fall back to the class's simple name") {
    class WeirdException(msg: String) extends RuntimeException(msg)
    val e = new WeirdException("odd")
    e.setStackTrace(Array(new StackTraceElement("aMain", "main", "a.on", 9)))
    RuntimeErrorReporter.render(e, "a.on") should include("WeirdException")
    RuntimeErrorReporter.render(e, "a.on") should include("odd")
  }
