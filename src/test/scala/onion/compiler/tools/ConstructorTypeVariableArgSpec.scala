package onion.compiler.tools

import onion.tools.Shell

/** A constructor argument is checked against an abstract `T` slot (#2332). */
class ConstructorTypeVariableArgSpec extends AbstractShellSpec {
  private def program(arg: String, tparams: String = "[T, U]", field: String = "U") =
    s"""class Box[T] { val v: T
       |public:
       |  def this(x: T) { v = x }
       |  def get(): T = v }
       |class S$tparams {
       |  val u: $field
       |public:
       |  def this(y: $field) { u = y }
       |  def mk(): Box[T] = new Box[T]($arg)
       |}
       |def main(args: String[]): Int { return 0 }
       |""".stripMargin

  describe("new Box[T](arg) inside a generic class") {
    it("rejects an unrelated type variable U (E0000)") {
      assert(Shell.Failure(-1) == shell.run(program("u"), "None", Array()))
    }
    it("still accepts a T") {
      assert(Shell.Success(0) == shell.run(program("u", "[T]", "T"), "None", Array()))
    }
  }
}
