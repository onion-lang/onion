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
  }
}
