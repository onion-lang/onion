package onion.compiler.tools

import onion.tools.Shell

/** A call on `this` (bare or written) sees the class's own type variables, not their bounds (#2547). */
class SelfCallTypeVariableSpec extends AbstractShellSpec {
  describe("a self-call in a class with an F-bounded type parameter") {
    it("passes a T where the method takes Comparable[T]") {
      val r = shell.run(
        """class J[T extends Comparable[T]] {
          |public:
          |  def cmp(c: Comparable[T]): Int = 7
          |  def go(t: T): Int = cmp(t)
          |}
          |def main(args: String[]): Int { return new J[String]().go("x") }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(7) == r)
    }
    it("picks the most specific overload, as Java does, for f(t) and this.f(t)") {
      val r = shell.run(
        """class J[T extends Comparable[T]] {
          |public:
          |  def f(c: Comparable[T]): Int = 1
          |  def f(o: Object): Int = 2
          |  def go(t: T): Int = f(t) * 10 + this.f(t)
          |}
          |def main(args: String[]): Int { return new J[String]().go("x") }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(11) == r)
    }
    it("passes a T from inside a closure in the class body") {
      val r = shell.run(
        """class J[T extends Comparable[T]] {
          |public:
          |  def cmp(c: Comparable[T]): Int = 7
          |  def go(t: T): Int { val g: () -> Int = () -> cmp(t); return g() }
          |}
          |def main(args: String[]): Int { return new J[String]().go("x") }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(7) == r)
    }
    it("still rejects a T where an unrelated Comparable is expected") {
      val r = shell.run(
        """class J[T extends Comparable[T]] {
          |public:
          |  def cmp(c: Comparable[String]): Int = 7
          |  def go(t: T): Int = cmp(t)
          |}
          |def main(args: String[]): Int { return 0 }
          |""".stripMargin, "None", Array())
      assert(Shell.Failure(-1) == r)
    }
    it("still lets an inherited method see the subclass's own variable") {
      val r = shell.run(
        """class Base[T extends Comparable[T]] {
          |public:
          |  def cmp(c: Comparable[T]): Int = 7
          |}
          |class Sub[U extends Comparable[U]] extends Base[U] {
          |public:
          |  def go(u: U): Int = cmp(u)
          |}
          |def main(args: String[]): Int { return new Sub[String]().go("x") }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(7) == r)
    }
    it("rejects a List[Number] where a self-call takes List[T] (#2536)") {
      val r = shell.run(
        """import { java.util.* }
          |class J[T extends Number] {
          |public:
          |  def first(xs: List[T]): T = xs[0]
          |  def go(ys: List[Number]): T = first(ys)
          |}
          |def main(args: String[]): Int { return 0 }
          |""".stripMargin, "None", Array())
      assert(Shell.Failure(-1) == r)
    }
  }
}
