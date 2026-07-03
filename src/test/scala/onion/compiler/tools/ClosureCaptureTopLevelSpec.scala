package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression for #214: a closure that captures a mutable top-level `var`/`val`
 * must share the same storage cell as the enclosing top-level scope, exactly
 * like a closure inside a function body. Previously the top-level (synthesized
 * `start` method) never marked captured variables as boxed, so each closure got
 * a disconnected copy and outer mutations were silently lost.
 *
 * A classless top-level script's synthesized `main` returns void, so the result
 * is observed through a unique-keyed system property that the script writes.
 * (System.out capture would not be safe under ScalaTest's parallel suites.)
 */
class ClosureCaptureTopLevelSpec extends AbstractShellSpec {
  /** Run a bare top-level script that writes its result via System::setProperty. */
  private def runReadingProperty(key: String, script: String): String = {
    System.clearProperty(key)
    shell.run(script, "None", Array())
    System.getProperty(key)
  }

  describe("closure capturing a mutable top-level var") {
    it("propagates the closure's mutation back to the outer variable (#214)") {
      val v = runReadingProperty(
        "test214.a",
        """
          |var c = 0
          |val f = () -> { c = c + 1; c }
          |f.call(); f.call()
          |System::setProperty("test214.a", "" + c)
          |""".stripMargin
      )
      assert(v == "2")
    }

    it("shares one cell across two distinct closures") {
      val v = runReadingProperty(
        "test214.b",
        """
          |var c = 0
          |val f = () -> { c = c + 1; c }
          |val g = () -> { c }
          |f.call()
          |System::setProperty("test214.b", g.call() + "")
          |""".stripMargin
      )
      assert(v == "1")
    }

    it("lets a read-only closure observe later outer mutations") {
      val v = runReadingProperty(
        "test214.c",
        """
          |var c = 7
          |val f = () -> { c }
          |val first = f.call()
          |c = 9
          |System::setProperty("test214.c", first + "," + f.call())
          |""".stripMargin
      )
      assert(v == "7,9")
    }

    it("works for a non-int captured var (Long)") {
      val v = runReadingProperty(
        "test214.d",
        """
          |var c = 0L
          |val f = () -> { c = c + 1L; c }
          |f.call(); f.call(); f.call()
          |System::setProperty("test214.d", "" + c)
          |""".stripMargin
      )
      assert(v == "3")
    }
  }

  describe("unrelated top-level behavior is preserved") {
    it("still mutates a top-level var directly without a closure") {
      val v = runReadingProperty(
        "test214.e",
        """
          |var c = 0
          |c = c + 5
          |System::setProperty("test214.e", "" + c)
          |""".stripMargin
      )
      assert(v == "5")
    }

    it("still shares a captured mutable local inside a method body") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    var c = 0
          |    val f = () -> { c = c + 1; c }
          |    f.call(); f.call()
          |    return c
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(2) == result)
    }
  }
}
