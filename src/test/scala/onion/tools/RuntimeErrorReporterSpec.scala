package onion.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Runtime errors are presented like diagnostics (issue #450): the position the user
 * can act on, frames they actually wrote, a name in the language's own vocabulary, and
 * an escape hatch to the untouched JVM trace.
 */
class RuntimeErrorReporterSpec extends AnyFunSpec {

  /** A throwable carrying a stack trace shaped like a real compiled script's. */
  private def withTrace(t: Throwable, frames: (String, String, String, Int)*): Throwable = {
    t.setStackTrace(frames.map { case (cls, method, file, line) =>
      new StackTraceElement(cls, method, file, line)
    }.toArray)
    t
  }

  private val scriptFrames = Seq(
    ("aMain", "main", "a.on", 2),
    ("aMain", "start", "a.on", 1),      // synthesized wrapper
    ("aMain", "main", "a.on", -1),      // synthesized entry, no position
    ("jdk.internal.reflect.DirectMethodHandleAccessor", "invoke", "X.java", 104),
    ("java.lang.reflect.Method", "invoke", "Method.java", 565)
  )

  describe("what the user is shown") {
    it("leads with the position of the first frame they wrote") {
      val out = RuntimeErrorReporter.render(
        withTrace(new ArithmeticException("/ by zero"), scriptFrames: _*), "a.on")
      assert(out.startsWith("a.on:2: error:"), out)
    }

    it("names the failure in the language's vocabulary, not the JVM class") {
      val out = RuntimeErrorReporter.render(
        withTrace(new ArithmeticException("/ by zero"), scriptFrames: _*), "a.on")
      assert(out.contains("division by zero"), out)
      assert(!out.contains("ArithmeticException"), out)
    }

    it("does not repeat the JVM message when the headline already says it") {
      val out = RuntimeErrorReporter.render(
        withTrace(new ArithmeticException("/ by zero"), scriptFrames: _*), "a.on")
      assert(!out.contains("division by zero: / by zero"), out)
    }

    it("keeps a JVM message that adds information") {
      val out = RuntimeErrorReporter.render(
        withTrace(new ArrayIndexOutOfBoundsException("Index 5 out of bounds for length 2"),
          ("bMain", "main", "b.on", 3)), "b.on")
      assert(out.contains("array index out of range"), out)
      assert(out.contains("Index 5 out of bounds for length 2"), out)
    }

    it("hides synthesized wrappers and everything below the launcher") {
      val out = RuntimeErrorReporter.render(
        withTrace(new ArithmeticException("/ by zero"), scriptFrames: _*), "a.on")
      assert(!out.contains("start"), out)
      assert(!out.contains("DirectMethodHandleAccessor"), out)
      assert(!out.contains("Method.java"), out)
    }

    it("shows the call path, capped, saying how many were dropped") {
      val deep = (1 to 40).map(i => ("cMain", if (i % 2 == 0) "g" else "f", "c.on", i % 2 + 1))
      val out = RuntimeErrorReporter.render(
        withTrace(new StackOverflowError(), deep: _*), "c.on")
      assert(out.contains("called from c.on:"), out)
      assert(out.contains("more frames"), out)
      assert(out.linesIterator.count(_.contains("called from")) <= 10, out)
    }

    it("explains a stack overflow instead of just naming it") {
      val out = RuntimeErrorReporter.render(
        withTrace(new StackOverflowError(), ("cMain", "f", "c.on", 1)), "c.on")
      assert(out.contains("stack overflow"), out)
      assert(out.contains("tail-call optimization") || out.contains("Tail-call optimization"), out)
    }

    it("points at --stacktrace") {
      val out = RuntimeErrorReporter.render(
        withTrace(new RuntimeException("boom"), ("aMain", "main", "a.on", 2)), "a.on")
      assert(out.contains("--stacktrace"), out)
    }
  }

  describe("failures with nothing of the user's in the trace") {
    it("falls back to the script name and says why there is no position") {
      val out = RuntimeErrorReporter.render(
        withTrace(new RuntimeException("internal"),
          ("java.util.ArrayList", "get", "ArrayList.java", 10)), "z.on")
      assert(out.startsWith("z.on: error:"), out)
      assert(out.contains("outside the script's own code"), out)
    }
  }

  describe("an exception with no friendly name") {
    it("uses the simple class name rather than inventing one") {
      class WeirdProblem(msg: String) extends RuntimeException(msg)
      val out = RuntimeErrorReporter.render(
        withTrace(new WeirdProblem("odd"), ("aMain", "main", "a.on", 5)), "a.on")
      assert(out.contains("WeirdProblem"), out)
      assert(out.contains("odd"), out)
    }
  }
}
