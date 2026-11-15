package onion.compiler.tools

import onion.tools.Shell

/**
 * A lambda inside a try-with-resources block can read the resource variable (#3031). It used
 * to compile, then fail JVM verification: the binding was captured, so readers expected a box,
 * but the resource was stored unboxed.
 */
class ClosureResourceVariableSpec extends AbstractShellSpec {
  describe("a lambda that reads a try-with-resources variable (#3031)") {
    def run(body: String, file: String): Shell.Result = shell.run(
      s"""
         |class Log conforms AutoCloseable {
         |  var events: String
         |public:
         |  def this { events = "" }
         |  def note(s: String): void { events = events + s }
         |  def close(): void { events = events + "/closed" }
         |  def seen: String = events
         |}
         |class Test {
         |public:
         |  static def call(f: () -> String): String = f()
         |  static def main(args: String[]): String {
         |    $body
         |    return "none"
         |  }
         |}
         |""".stripMargin, file, Array())

    it("reads the resource in a plain try-with-resources") {
      val result = run(
        """try (val r = new java.io.StringReader("abc")) { return call { -> "c=" + r.read() } }""",
        "ResourceCapturedPlain.on")
      assert(result == Shell.Success("c=97"))
    }

    it("still closes the resource after the lambda used it") {
      val result = run(
        """val log = new Log()
          |    try (val r = log) { call { -> r.note("used"); "" } }
          |    return log.seen""".stripMargin,
        "ResourceCapturedClosed.on")
      assert(result == Shell.Success("used/closed"))
    }

    it("reads the resource with a catch and a finally") {
      val result = run(
        """var out = ""
          |    try (val r = new java.io.StringReader("abc")) {
          |      out = call { -> "c=" + r.read() }
          |      throw new Exception("x")
          |    } catch e: Exception { out = out + "/" + e.getMessage() } finally { out = out + "/f" }
          |    return out""".stripMargin,
        "ResourceCapturedCatchFinally.on")
      assert(result == Shell.Success("c=97/x/f"))
    }

    it("reads the resource with only a finally") {
      val result = run(
        """var out = ""
          |    try (val r = new java.io.StringReader("abc")) { out = call { -> "c=" + r.read() } } finally { out = out + "/f" }
          |    return out""".stripMargin,
        "ResourceCapturedFinally.on")
      assert(result == Shell.Success("c=97/f"))
    }

    it("reads one of two resources, and both are still closed") {
      val result = run(
        """val a = new Log()
          |    val b = new Log()
          |    try (val x = a; val y = b) { call { -> y.note("y"); "" } }
          |    return a.seen + "|" + b.seen""".stripMargin,
        "ResourceCapturedSecond.on")
      assert(result == Shell.Success("/closed|y/closed"))
    }

    it("reads the resource of a try inside an outer lambda") {
      val result = run(
        """val g: () -> String = () -> {
          |      try (val r = new java.io.StringReader("abc")) { return call { -> "c=" + r.read() } }
          |      return "none"
          |    }
          |    return g()""".stripMargin,
        "ResourceCapturedNested.on")
      assert(result == Shell.Success("c=97"))
    }
  }
}
