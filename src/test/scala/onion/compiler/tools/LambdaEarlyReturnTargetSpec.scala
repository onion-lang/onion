package onion.compiler.tools

import onion.tools.Shell

/**
 * Issue #3117: an early `return` in a block lambda passed to a generic method was
 * not checked against the type the call's target pins. The early return and the
 * last expression joined to a raw type (`Option`), which fit the target
 * `Option[Int]`, so `return Option::some("str")` compiled and the program failed
 * later with ClassCastException.
 */
class LambdaEarlyReturnTargetSpec extends AbstractShellSpec {

  private def run(body: String, name: String) =
    shell.run("import { onion.Option; java.util.List; }\n" + body, name, Array())

  describe("an early return in a lambda is checked against the call's target type (#3117)") {
    it("Option[String] returned early where the target pins Option[Int] (the issue's repro)") {
      val result = run(
        """def ch2[U](f: () -> U): U = f()
          |val c: Option[Int] = ch2(() -> {
          |  if 1 < 2 { return Option::some("str") }
          |  Option::none()
          |})
          |def main(args: String[]): String = "" + (c.get() + 1)
          |""".stripMargin,
        "EarlyReturnOption.on"
      )
      assert(Shell.Failure(-1) == result)
    }

    it("a list literal returned early, and a concrete last expression") {
      val list = run(
        """def ch2[U](f: () -> U): U = f()
          |val c: List[Int] = ch2(() -> {
          |  if 1 < 2 { return ["s"] }
          |  [1]
          |})
          |def main(args: String[]): String = "" + (c[0] + 1)
          |""".stripMargin,
        "EarlyReturnList.on"
      )
      assert(Shell.Failure(-1) == list)
      val some = run(
        """def ch2[U](f: () -> U): U = f()
          |val c: Option[Int] = ch2(() -> {
          |  if 1 < 2 { return Option::some("str") }
          |  Option::some(3)
          |})
          |def main(args: String[]): String = "" + c
          |""".stripMargin,
        "EarlyReturnSome.on"
      )
      assert(Shell.Failure(-1) == some)
    }

    it("well-typed early returns still compile and run") {
      val result = run(
        """def ch2[U](f: () -> U): U = f()
          |def pick(b: Boolean): Option[Int] = ch2(() -> {
          |  if b { return Option::some(7) }
          |  Option::none()
          |})
          |def main(args: String[]): String = "" + pick(true) + " " + pick(false)
          |""".stripMargin,
        "EarlyReturnOk.on"
      )
      assert(Shell.Success("Some[value=7] None[]") == result)
    }
  }
}
