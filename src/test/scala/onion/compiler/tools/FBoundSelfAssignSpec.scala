package onion.compiler.tools

import onion.tools.Shell

/** A `T extends Comparable[T]` is a `Comparable[T]` wherever one is expected (#2338). */
class FBoundSelfAssignSpec extends AbstractShellSpec {
  describe("a type variable with an F-bound") {
    it("is accepted where its own bound is expected") {
      val r = shell.run(
        """class J[T extends Comparable[T]] {
          |public:
          |  def asCmp(t: T): Comparable[T] = t
          |}
          |def main(args: String[]): Int { return new J[String]().asCmp("a").compareTo("b") + 2 }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(1) == r)
    }
    it("is accepted as an argument of its own bound's type") {
      val r = shell.run(
        """def pick[T extends Comparable[T]](a: T, b: T): Int {
          |  val c: Comparable[T] = a
          |  return c.compareTo(b)
          |}
          |def main(args: String[]): Int { return pick(5, 3) }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(1) == r)
    }
    it("is still rejected where an unrelated applied type is expected") {
      val r = shell.run(
        """class J[T extends Comparable[T]] {
          |public:
          |  def bad(t: T): Comparable[String] = t
          |}
          |def main(args: String[]): Int { return 0 }
          |""".stripMargin, "None", Array())
      assert(Shell.Failure(-1) == r)
    }
  }
}
