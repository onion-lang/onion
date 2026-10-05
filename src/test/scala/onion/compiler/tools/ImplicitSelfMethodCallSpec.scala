package onion.compiler.tools

import onion.tools.Shell

/**
 * Implicit self method call (#2013): a bare, receiver-less, parens-less
 * reference to another no-arg method of the same class resolves against the
 * implicit `self`/`this`, the same way a bare field reference already does
 * (#162) -- so `greet` inside the class works like `self.greet`/`greet()`.
 * A field of the same name still takes priority, and a truly unresolvable
 * name is still rejected.
 */
class ImplicitSelfMethodCallSpec extends AbstractShellSpec {
  describe("implicit self method call") {
    it("calls a sibling no-arg method by bare name") {
      val result = shell.run(
        """
          |class C {
          |public:
          |  def greet: String = "hi"
          |  def wrap: String = greet
          |  static def main(args: String[]): String = new C().wrap
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("hi") == result)
    }

    it("calls an abstract method by bare name from a concrete method (template method)") {
      val result = shell.run(
        """
          |abstract class Shape {
          |public:
          |  abstract def area: Double
          |  def describe: String = "area=" + area
          |}
          |class Square extends Shape {
          |  val side: Double
          |public:
          |  def this(side: Double) { this.side = side }
          |  override def area: Double = side * side
          |}
          |class Launcher {
          |public:
          |  static def main(args: String[]): String = new Square(3.0).describe
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("area=9.0") == result)
    }

    it("still lets a field of the same name take priority over a method") {
      val result = shell.run(
        """
          |class C {
          |  val x: Int
          |public:
          |  def this() { this.x = 1 }
          |  def x(): Int = 2
          |  def read(): Int = x
          |  static def main(args: String[]): Int = new C().read()
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(1) == result)
    }

    it("still rejects a truly unresolvable bare name") {
      val result = shell.run(
        """
          |class C {
          |public:
          |  def f(): Int = thisNameDoesNotExistAnywhere
          |  static def main(args: String[]): Int = new C().f()
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }
  }
}
