package onion.compiler.tools

import onion.tools.Shell

/**
 * #3131: in a `void` method a tail call is a bare statement (`b(n - 1)`), not a
 * `return b(n - 1)`. The mutual-recursion pass only looked for `return`, so a `void`
 * `@TailRecursive` group overflowed the stack with no W0016.
 */
class MutualRecursionVoidSpec extends AbstractShellSpec {
  private def run(src: String): (Shell.Result, String) = {
    val originalOut = System.out
    val buffer = new java.io.ByteArrayOutputStream()
    System.setOut(new java.io.PrintStream(buffer, true, "UTF-8"))
    val result =
      try shell.run(src, "MRV.on", Array())
      finally System.setOut(originalOut)
    (result, buffer.toString("UTF-8").trim)
  }

  private def program(aBody: String, bBody: String, n: Int = 3000000): String =
    s"""
       |class M {
       |public:
       |  static def main(args: String[]): void { a($n, 0); IO::println("done") }
       |private:
       |  static @TailRecursive def a(n: Int, s: Int): void {
       |$aBody
       |  }
       |  static @TailRecursive def b(n: Int, s: Int): void {
       |$bBody
       |  }
       |}
       |""".stripMargin

  describe("a void @TailRecursive mutual group") {
    it("is stack-safe when the tail calls are bare statements (#3131)") {
      val (result, out) = run(program(
        """    if n <= 0 { IO::println("s=" + s); return }
          |    if n % 2 == 0 { s = s + 1 }
          |    b(n - 1, s)""".stripMargin,
        """    if n <= 0 { IO::println("s=" + s); return }
          |    a(n - 1, s + 2)""".stripMargin))
      assert(!result.isInstanceOf[Shell.Failure])
      assert(out == "s=4500000\ndone")
    }

    it("is stack-safe when the bare tail call is in an if/else") {
      val (result, out) = run(program(
        """    if n <= 0 { IO::println("s=" + s) } else { b(n - 1, s + 1) }""",
        """    if n <= 0 { IO::println("s=" + s) } else { a(n - 1, s + 1) }"""))
      assert(!result.isInstanceOf[Shell.Failure])
      assert(out == "s=3000000\ndone")
    }

    it("stops when a member falls off its end instead of looping") {
      val (result, out) = run(program(
        """    if n > 0 { b(n - 1, s + 1) }""",
        """    if n > 0 { a(n - 1, s + 1) } else { IO::println("s=" + s) }""", n = 8))
      assert(!result.isInstanceOf[Shell.Failure])
      assert(out == "done")
    }

    it("runs a statement after a call that is not in tail position") {
      val (result, out) = run(program(
        """    if n <= 0 { return }
          |    b(n - 1, s + 1)
          |    IO::println("back " + n)""".stripMargin,
        """    if n <= 0 { return }
          |    a(n - 1, s + 1)""".stripMargin, n = 3))
      assert(!result.isInstanceOf[Shell.Failure])
      assert(out == "back 1\nback 3\ndone")
    }
  }
}
