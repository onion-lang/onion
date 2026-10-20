package onion.compiler.tools

import onion.tools.Shell

/**
 * A type parameter bounded by another type parameter of the same declaration
 * (`[B, T extends B]`) is accepted where its own bound is expected — the bound
 * declares exactly that relationship (#2259).
 */
class ChainedTypeVariableBoundSpec extends AbstractShellSpec {
  describe("a type variable bounded by another type variable") {
    it("is returned where its bound is expected") {
      val r = shell.run(
        """class Holder[B, T extends B] {
          |  var item: T
          |public:
          |  def this(v: T) { item = v }
          |  def asB(): B { return item }
          |}
          |def main(args: String[]): Int {
          |  val h: Holder[String, String] = new Holder[String, String]("hello")
          |  return h.asB().length()
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(5) == r)
    }
    it("is assignable to a local of its bound's type") {
      val r = shell.run(
        """class Holder[B, T extends B] {
          |  var item: T
          |public:
          |  def this(v: T) { item = v }
          |  def asB(): B { val b: B = item; return b }
          |}
          |def main(args: String[]): Int {
          |  val h: Holder[String, String] = new Holder[String, String]("hi")
          |  return h.asB().length()
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(2) == r)
    }
  }
}
