package onion.compiler.tools

import onion.tools.Shell

class OverrideCheckSpec extends AbstractShellSpec {
  describe("override keyword target checking (issue #268)") {
    it("rejects an override method that overrides nothing") {
      val result = shell.run(
        """
          |class Base {
          |public:
          |  def foo(): Int { return 1 }
          |}
          |class Derived : Base {
          |public:
          |  override def bar(): Int { return 2 }
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): Int { return 0 }
          |}
        """.stripMargin,
        "OverrideNothing.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("rejects an override method whose arity does not match any base method") {
      val result = shell.run(
        """
          |class Base {
          |public:
          |  def foo(x: Int): Int { return x }
          |}
          |class Derived : Base {
          |public:
          |  override def foo(): Int { return 0 }
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): Int { return 0 }
          |}
        """.stripMargin,
        "OverrideWrongArity.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("accepts a real override of a base class method") {
      val result = shell.run(
        """
          |class Base {
          |public:
          |  def foo(): Int { return 1 }
          |}
          |class Derived : Base {
          |public:
          |  override def foo(): Int { return 2 }
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): Int { return new Derived().foo() }
          |}
        """.stripMargin,
        "OverrideBase.on",
        Array()
      )
      assert(Shell.Success(2) == result)
    }

    it("accepts a real override of an interface method") {
      val result = shell.run(
        """
          |interface Greeter {
          |  def greet(): String
          |}
          |class Hello <: Greeter {
          |public:
          |  override def greet(): String { return "hi" }
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): String { return (new Hello() as Greeter).greet() }
          |}
        """.stripMargin,
        "OverrideInterface.on",
        Array()
      )
      assert(Shell.Success("hi") == result)
    }

    it("accepts an override of a generic interface method specialized to a primitive") {
      val result = shell.run(
        """
          |interface Box[T] {
          |  def get(): T
          |}
          |class IntBox <: Box[Int] {
          |public:
          |  override def get(): Int { return 42 }
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): Int { return (new IntBox() as Box[Int]).get() }
          |}
        """.stripMargin,
        "OverrideGeneric.on",
        Array()
      )
      assert(Shell.Success(42) == result)
    }

    it("accepts an override of java.lang.Object.toString") {
      val result = shell.run(
        """
          |class Thing {
          |public:
          |  override def toString(): String { return "thing" }
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): String { return new Thing().toString() }
          |}
        """.stripMargin,
        "OverrideToString.on",
        Array()
      )
      assert(Shell.Success("thing") == result)
    }
  }
}
