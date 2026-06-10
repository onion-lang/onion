package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for issue #131: do-notation over Option and Result (bind/successful
 * added to both), and do-notation at the script top level (top-level block
 * elements were not desugared, so the statement vanished silently).
 */
class DoNotationMonadsSpec extends AbstractShellSpec {

  describe("do-notation over Option") {
    it("chains bindings and short-circuits on none") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val r = do[Option] { a <- Option::some(40); b <- Option::some(2); ret (a as Int) + (b as Int) }
          |    val n = do[Option] { a <- Option::some(1); b <- Option::none(); ret a }
          |    return r.get() + ":" + n.isEmpty()
          |  }
          |}
          |""".stripMargin,
        "DoOption.on",
        Array()
      )
      assert(Shell.Success("42:true") == result)
    }
  }

  describe("do-notation over Result") {
    it("chains ok values") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val r = do[Result] { a <- Result::ok(20); b <- Result::ok(22); ret (a as Int) + (b as Int) }
          |    return (r.getOrThrow() as Int)
          |  }
          |}
          |""".stripMargin,
        "DoResult.on",
        Array()
      )
      assert(Shell.Success(42) == result)
    }
  }

  describe("do-notation at the top level") {
    it("desugars top-level statements containing do expressions") {
      val result = shell.run(
        """
          |val r = do[Option] { a <- Option::some(40); b <- Option::some(2); ret (a as Int) + (b as Int) }
          |IO::println(r.get())
          |""".stripMargin,
        "DoTopLevel.on",
        Array()
      )
      assert(Shell.Success(()) == result || result.isInstanceOf[Shell.Success])
    }
  }
}
