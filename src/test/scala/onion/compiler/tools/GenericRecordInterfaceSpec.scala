package onion.compiler.tools

import onion.tools.Shell

/**
 * A generic record may implement a generic interface parameterized by the
 * record's own type variable (`record Foo[T](v: T) <: Bar[T]`). The record's
 * type parameters must be in scope while its supertypes are resolved — a
 * generic class already worked, but a generic record used to fail with E0003
 * ("type Bar[T] not found") because its supertype clause was resolved outside
 * the type-parameter scope.
 */
class GenericRecordInterfaceSpec extends AbstractShellSpec {

  describe("a generic record implements a generic interface") {
    it("resolves the generic supertype and dispatches through it") {
      val result = shell.run(
        """
          | interface Bar[T] {
          |   def get(): T
          | }
          | record Foo[T](v: T) <: Bar[T] {
          | public:
          |   def get(): T = v()
          | }
          | static def main(args: String[]): String {
          |   val b: Bar[String] = new Foo[String]("hi")
          |   return b.get()
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("hi") == result)
    }

    it("still allows a generic record to implement a non-generic interface") {
      val result = shell.run(
        """
          | interface Named { def label(): String }
          | record Foo[T](v: T) <: Named {
          | public:
          |   def label(): String = "foo"
          | }
          | static def main(args: String[]): String {
          |   return (new Foo[String]("x")).label()
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("foo") == result)
    }
  }
}
