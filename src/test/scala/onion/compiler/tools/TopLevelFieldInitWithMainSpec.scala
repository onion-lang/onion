package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression for #270: a top-level `val`/`var` with an initializer must be
 * initialized before a user-defined `def main` runs. Previously the initializers
 * were only emitted into the synthetic `start` method, which is never invoked
 * when the user supplies their own `main`, so the static fields kept their
 * default (null/0/false) — a silent miscompile.
 *
 * A classless top-level script's user `main` returns whatever the user declares;
 * for void mains the observed result is written through a unique-keyed system
 * property (System.out capture is unsafe under ScalaTest's parallel suites).
 */
class TopLevelFieldInitWithMainSpec extends AbstractShellSpec {
  private def runReadingProperty(key: String, script: String, args: Array[String] = Array()): String = {
    System.clearProperty(key)
    shell.run(script, "None", args)
    System.getProperty(key)
  }

  describe("top-level field initializer with a user-defined main (#270)") {
    it("initializes a String val read directly from main") {
      val v = runReadingProperty(
        "test270.a",
        """
          |val greeting: String = "hi"
          |def main(args: String[]): void { System::setProperty("test270.a", greeting) }
          |""".stripMargin
      )
      assert(v == "hi")
    }

    it("initializes an Int val and returns it from main") {
      val r = shell.run(
        """
          |val answer: Int = 42
          |def main(args: String[]): Int { return answer }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(42) == r)
    }

    it("initializes a var read from a helper function") {
      val r = shell.run(
        """
          |var counter: Int = 41
          |def helper(): Int { return counter + 1 }
          |def main(args: String[]): Int { return helper() }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(42) == r)
    }

    it("initializes multiple fields of different types before main") {
      val v = runReadingProperty(
        "test270.d",
        """
          |val a: String = "foo"
          |val b: Int = 7
          |val c: Boolean = true
          |def main(args: String[]): void {
          |  System::setProperty("test270.d", a + " " + b + " " + c)
          |}
          |""".stripMargin
      )
      assert(v == "foo 7 true")
    }

    it("passes the program arguments through to a field initializer") {
      val v = runReadingProperty(
        "test270.e",
        """
          |val first: String = args[0]
          |def main(args: String[]): void { System::setProperty("test270.e", first) }
          |""".stripMargin, Array("hello")
      )
      assert(v == "hello")
    }

    it("still lets a helper mutate a top-level var initialized before main") {
      val r = shell.run(
        """
          |var count: Int = 10
          |def bump(): void { count = count + 5 }
          |def main(args: String[]): Int { bump()
          |  return count }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(15) == r)
    }

    it("does not run bare executable top-level statements when a main exists") {
      // Only the field initializer runs before main; the bare println is skipped
      // (unchanged behavior). The field value is observed to confirm init ran.
      val v = runReadingProperty(
        "test270.g",
        """
          |val greeting: String = "init"
          |System::setProperty("test270.g.bare", "ran")
          |def main(args: String[]): void { System::setProperty("test270.g", greeting) }
          |""".stripMargin
      )
      assert(v == "init")
      assert(System.getProperty("test270.g.bare") == null)
    }

    it("leaves a main-less script unaffected") {
      val v = runReadingProperty(
        "test270.h",
        """
          |val greeting: String = "hi"
          |System::setProperty("test270.h", greeting)
          |""".stripMargin
      )
      assert(v == "hi")
    }
  }
}
