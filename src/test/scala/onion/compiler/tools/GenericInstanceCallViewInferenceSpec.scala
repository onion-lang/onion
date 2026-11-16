package onion.compiler.tools

import onion.tools.Shell

/**
 * A plain class reaches a generic parameter through its own applied view of the
 * interface: `String` is a `Comparable[String]`, so `[T](c: Comparable[T])` binds
 * `T = String` for a top-level function and an instance method alike (#2845).
 * Before, the call was E0005 "a function applicable ... is not found".
 */
class GenericInstanceCallViewInferenceSpec extends AbstractShellSpec {
  describe("a generic parameter typed by an interface the argument's class implements") {
    it("binds T through String's Comparable view in a top-level function") {
      val r = shell.run(
        """def id[T](c: Comparable[T]): Comparable[T] = c
          |def main(args: String[]): Int { IO::println(id("a")); return 0 }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(0) == r)
    }
    it("binds T through the view in an instance method, with T fixed by a second argument") {
      val r = shell.run(
        """class U {
          |public:
          |  def pick[T](c: Comparable[T], d: T): T = d
          |}
          |def main(args: String[]): Int { IO::println(new U().pick("a", "b")); return 0 }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(0) == r)
    }
    it("still rejects a second argument that contradicts the view's T") {
      val r = shell.run(
        """class U {
          |public:
          |  def pick[T](c: Comparable[T], d: T): T = d
          |}
          |def main(args: String[]): Int { IO::println(new U().pick("a", 1)); return 0 }
          |""".stripMargin, "None", Array())
      assert(Shell.Failure(-1) == r)
    }
  }
}
