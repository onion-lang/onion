package onion.compiler.tools

import onion.tools.Shell

/**
 * F-bounded type parameters (a bound that references the parameter itself, e.g.
 * `T extends Comparable[T]`) resolve — the parameter is in scope while its own
 * bound is resolved.
 */
class FBoundedTypeParamSpec extends AbstractShellSpec {
  describe("F-bounded type parameters") {
    it("resolves an F-bound on a generic class and dispatches through it") {
      val r = shell.run(
        """class Box[T extends Comparable[T]] {
          |  val v: T
          |public:
          |  def this(x: T) { v = x }
          |  def cmp(o: T): Int { return v.compareTo(o) }
          |}
          |def main(args: String[]): Int { return new Box[Integer](5).cmp(3) }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(1) == r)
    }
    it("resolves an F-bound on a generic method") {
      val r = shell.run(
        """def largest[T extends Comparable[T]](a: T, b: T): T {
          |  if a.compareTo(b) >= 0 { return a } else { return b }
          |}
          |def main(args: String[]): Int { return largest(3, 9) }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(9) == r)
    }
    it("still resolves ordinary and concrete-argument bounds") {
      assert(Shell.Success(5.0) == shell.run(
        "class B[T extends Number] { val v: T\npublic: def this(x: T) { v = x }\n def d(): Double = v.doubleValue() }\ndef main(args: String[]): Double { return new B[Integer](5).d() }", "None", Array()))
    }
    it("accepts a conforming argument on a static method of an F-bounded generic class (#1987)") {
      val r = shell.run(
        """class Holder extends Object conforms Comparable[Holder] {
          |  val v: Int
          |public:
          |  def this(v: Int) { this.v = v }
          |  def compareTo(o: Holder): Int { return v - o.v }
          |  def value: Int { return v }
          |}
          |class MaxFinder[T extends Comparable[T]] {
          |public:
          |  static def max(a: T, b: T): T {
          |    if a.compareTo(b) >= 0 { return a } else { return b }
          |  }
          |}
          |def main(args: String[]): Int { return (MaxFinder::max(new Holder(3), new Holder(8)) as Holder).value }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(8) == r)
    }
  }
}
