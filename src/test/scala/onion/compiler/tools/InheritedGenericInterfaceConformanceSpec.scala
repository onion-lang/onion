package onion.compiler.tools

import onion.tools.Shell

/**
 * A subclass that fixes a generic superclass's type parameter to a concrete type, without
 * redeclaring the inherited method, was falsely rejected with E0037 (#2074). The superclass's
 * own concrete method satisfies the interface the superclass conforms to; the subclass need not
 * repeat it.
 */
class InheritedGenericInterfaceConformanceSpec extends AbstractShellSpec {
  describe("E0037 abstract-method-implementation check across a generic inheritance chain") {
    it("accepts a subclass that fixes the superclass's type parameter without redeclaring the method") {
      val r = shell.run(
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
          |def main(args: String[]): String {
          |  val c: Concrete = new Concrete()
          |  return c.apply("hi")
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("hi") == r)
    }

    it("still rejects a subclass whose inherited method does not satisfy the interface") {
      val r = shell.run(
        """
          |interface Foo[T] {
          |  def apply(x: T): T
          |}
          |class Base[T] {
          |public:
          |  def apply(x: T): T = x
          |}
          |class Concrete extends Base[String] conforms Foo[Int] {
          |}
          |""".stripMargin, "None", Array())
      assert(r.isInstanceOf[Shell.Failure])
    }
  }
}
