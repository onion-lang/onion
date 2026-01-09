package onion.compiler.tools

import onion.tools.Shell

class AbstractClassSpec extends AbstractShellSpec {
  describe("Abstract class and method support") {
    it("allows concrete subclass to override abstract method") {
      val result = shell.run(
        """
          |abstract class Animal {
          |public:
          |  abstract def speak(): String;
          |}
          |class Dog : Animal {
          |public:
          |  def speak(): String { return "Woof!"; }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val dog: Animal = new Dog();
          |    return dog.speak();
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("Woof!") == result)
    }

    it("allows abstract class with concrete methods") {
      val result = shell.run(
        """
          |abstract class Shape {
          |public:
          |  abstract def area(): Int;
          |}
          |class Square : Shape {
          |  var side: Int;
          |public:
          |  def this(s: Int) { this.side = s; }
          |  def area(): Int { return this.side * this.side; }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val s: Shape = new Square(5);
          |    return s.area();
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(25) == result)
    }

    it("supports multiple levels of abstract inheritance") {
      val result = shell.run(
        """
          |abstract class Base {
          |public:
          |  abstract def getValue(): Int;
          |}
          |abstract class Middle : Base {
          |public:
          |  def getDoubleValue(): Int { return getValue() * 2; }
          |}
          |class Concrete : Middle {
          |public:
          |  def getValue(): Int { return 10; }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val c: Middle = new Concrete();
          |    return c.getDoubleValue();
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(20) == result)
    }
  }
}
