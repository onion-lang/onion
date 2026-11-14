package onion.compiler.tools

import onion.tools.Shell

/**
 * A generic constructor may omit its type arguments when the expected type pins
 * them (constructor diamond): `val b: Box[String] = new Box("x")`. When no
 * expected type supplies them they are inferred from the constructor arguments
 * instead (issue #305, covered by GenericConstructorInferenceSpec); a bare
 * generic that nothing pins is still rejected (E0066).
 */
class ConstructorDiamondSpec extends AbstractShellSpec {
  private val box =
    """
      |class Box[T] {
      |  val v: T
      |public:
      |  def this(x: T) { v = x }
      |  def get(): T = v
      |}
      |""".stripMargin

  describe("constructor diamond from the expected type") {
    it("infers the type argument from a val annotation") {
      val r = shell.run(box +
        "def main(args: String[]): String { val b: Box[String] = new Box(\"hi\")\n return b.get() }", "None", Array())
      assert(Shell.Success("hi") == r)
    }
    it("infers a primitive type argument") {
      val r = shell.run(box +
        "def main(args: String[]): Int { val b: Box[Int] = new Box(42)\n return b.get() }", "None", Array())
      assert(Shell.Success(42) == r)
    }
    it("infers from a return type, and for multiple type parameters") {
      val r = shell.run(
        """
          |class Pair[A, B] {
          |  val a: A
          |  val b: B
          |public:
          |  def this(x: A, y: B) { a = x
          |    b = y }
          |  def first(): A = a
          |}
          |def make(): Pair[String, Int] { return new Pair("k", 9) }
          |def main(args: String[]): String { return make().first() }
          |""".stripMargin, "None", Array())
      assert(Shell.Success("k") == r)
    }
    it("still accepts explicit type arguments") {
      val r = shell.run(box +
        "def main(args: String[]): String { val b: Box[String] = new Box[String](\"x\")\n return b.get() }", "None", Array())
      assert(Shell.Success("x") == r)
    }
    it("falls back to argument inference when there is no expected type") {
      // Previously E0066: with no expected type the bare generic was rejected.
      // The constructor arguments now pin T the same way a generic method call
      // infers from its arguments (#305).
      assert(Shell.Success("hi") == shell.run(
        box + "def main(args: String[]): String { val b = new Box(\"hi\")\n return b.get() }", "None", Array()))
    }

    it("still rejects a bare generic constructor that nothing pins") {
      assert(Shell.Failure(-1) == shell.run(
        "class Empty[T] { public: def this {} }\ndef main(args: String[]): Int { val e = new Empty()\n return 0 }",
        "None", Array()))
    }

    it("checks a bound that names another type parameter against the inferred arguments (#2539)") {
      val p =
        """
          |class P[B, T extends B] {
          |public:
          |  val b: B
          |  val t: T
          |  def this(b: B, t: T) { this.b = b; this.t = t }
          |  def get(): B = (t as B)
          |}
          |""".stripMargin
      assert(Shell.Failure(-1) == shell.run(p +
        "def main(args: String[]): String { val p = new P(\"a\", Integer::valueOf(1))\n return p.get() }", "None", Array()))
      assert(Shell.Success("b") == shell.run(p +
        "def main(args: String[]): String { val p = new P(\"a\", \"b\")\n return p.get() }", "None", Array()))
    }

    it("reads a nullable argument for the named parameter as its class when checking the bound (#2539 review)") {
      val p =
        """
          |class P[B, T extends B] {
          |public:
          |  val b: B
          |  val t: T
          |  def this(b: B, t: T) { this.b = b; this.t = t }
          |}
          |""".stripMargin
      // Inferred: B is String?, and "x" is a String
      assert(Shell.Success("x|x") == shell.run(p +
        "def f(ns: String?): String = new P(ns, \"x\").t\n" +
        "def main(args: String[]): String { return f(null) + \"|\" + f(\"y\") }", "None", Array()))
      // Explicit: the same instantiation written out
      assert(Shell.Success("x") == shell.run(p +
        "def main(args: String[]): String { val e: P[String?, String] = new P[String?, String](null, \"x\")\n return e.t }",
        "None", Array()))
      // Still checked: an unrelated class is not a String?
      assert(Shell.Failure(-1) == shell.run(p +
        "def f(ns: String?): Object = new P(ns, Integer::valueOf(1)).t\n" +
        "def main(args: String[]): Object { return f(null) }", "None", Array()))
    }
  }
}
