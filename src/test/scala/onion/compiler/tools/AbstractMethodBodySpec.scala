package onion.compiler.tools

import onion.tools.Shell

/**
 * An explicitly `abstract` method with a body is rejected (E0072): the body would
 * be dropped at codegen (silently ignored). An interface DEFAULT method (a body
 * with no `abstract` keyword) and a bodiless abstract method are unaffected.
 */
class AbstractMethodBodySpec extends AbstractShellSpec {
  it("rejects an abstract method that has a body") {
    val result = shell.run(
      """
        | abstract class B {
        | public:
        |   abstract def foo(): Int { return 99 }
        | }
        | class I : B { public: override def foo(): Int = 1 }
        | static def main(args: String[]): Int { return new I().foo() }
      """.stripMargin, "None", Array())
    assert(Shell.Failure(-1) == result)
  }

  it("allows a bodiless abstract method") {
    val result = shell.run(
      """
        | abstract class B {
        | public:
        |   abstract def foo(): Int
        | }
        | class I : B { public: override def foo(): Int = 7 }
        | static def main(args: String[]): Int { return new I().foo() }
      """.stripMargin, "None", Array())
    assert(Shell.Success(7) == result)
  }

  it("allows an interface default method (body, no abstract keyword)") {
    val result = shell.run(
      """
        | interface Greeter {
        |   def greet(): String { return "hi" }
        | }
        | class G <: Greeter { }
        | static def main(args: String[]): String { return (new G() as Greeter).greet() }
      """.stripMargin, "None", Array())
    assert(Shell.Success("hi") == result)
  }
}
