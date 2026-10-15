package onion.compiler.tools

import onion.tools.Shell

/** A subclass that fixes a generic superclass's type parameter inherits the superclass's
  * implementation of a generic interface method; E0037 must not ask for a redundant override. */
class InheritedGenericImplementationSpec extends AbstractShellSpec {
  it("accepts a subclass that inherits an interface method implemented by a generic superclass") {
    val result = shell.run(
      """
        |interface Foo[T] {
        |  def apply(x: T): T
        |}
        |class Base[T] conforms Foo[T] {
        |public:
        |  def apply(x: T): T = x
        |}
        |class Concrete extends Base[String] {
        |}
        |static def main(args: String[]): void {
        |  val c: Concrete = new Concrete()
        |  IO::println(c.apply("hi"))
        |}
      """.stripMargin, "None", Array())
    assert(result.isInstanceOf[Shell.Success])
  }

  it("still reports E0037 when the superclass implementation has a different parameter type") {
    val result = shell.run(
      """
        |interface Foo[T] {
        |  def apply(x: T): T
        |}
        |class Base[T] {
        |public:
        |  def apply(x: T): T = x
        |}
        |class Concrete extends Base[Int] conforms Foo[String] {
        |}
        |static def main(args: String[]): void { }
      """.stripMargin, "None", Array())
    assert(Shell.Failure(-1) == result)
  }
}
