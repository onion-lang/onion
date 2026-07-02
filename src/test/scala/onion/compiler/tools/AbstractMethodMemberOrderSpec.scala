package onion.compiler.tools

import onion.tools.Shell

/**
 * A body-less (abstract) method in a class/abstract-class body may be followed by
 * another member. This used to be a syntax error unless the abstract method was
 * last (the no-body method_decl branch did not enter statement mode before its
 * terminator).
 */
class AbstractMethodMemberOrderSpec extends AbstractShellSpec {
  it("allows a member after a body-less abstract method") {
    val r = shell.run(
      """
        |abstract class Shape {
        |public:
        |  abstract def area(): Double
        |  abstract def perimeter(): Double
        |}
        |class Square : Shape {
        |public:
        |  def this {}
        |  def area(): Double = 4.0
        |  def perimeter(): Double = 8.0
        |}
        |def main(args: String[]): Double { val s: Shape = new Square()
        |  return s.area() }
        |""".stripMargin, "None", Array())
    assert(Shell.Success(4.0) == r)
  }
}
