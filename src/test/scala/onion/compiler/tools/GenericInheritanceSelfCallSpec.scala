package onion.compiler.tools

import onion.tools.Shell

/**
 * Issue #271: a class extending a generic parent with a concrete type argument
 * must specialize the parent's type parameter when an inherited method is called
 * unqualified (via self). `class StrBox : Box[String]` calling inherited
 * `Box.get(): T` must see `String`, not the raw `T`.
 */
class GenericInheritanceSelfCallSpec extends AbstractShellSpec {
  describe("self-call of inherited generic method specializes parent type parameter") {
    it("String type argument: get() seen as String") {
      val result = shell.run(
        """
          | class Box[T] {
          |   val value: T
          | public:
          |   def this(v: T) { value = v }
          |   def get(): T { return value }
          | }
          | class StrBox(v: String) : Box[String](v) {
          | public:
          |   def upper(): String {
          |     val g: String = get()
          |     return g.toUpperCase()
          |   }
          | }
          | class Main {
          | public:
          |   static def main(args: String[]): String {
          |     return new StrBox("hello").upper()
          |   }
          | }
        """.stripMargin,
        "Main",
        Array()
      )
      assert(Shell.Success("HELLO") == result)
    }

    it("Integer type argument: get() usable as Int") {
      val result = shell.run(
        """
          | class Box[T] {
          |   val value: T
          | public:
          |   def this(v: T) { value = v }
          |   def get(): T { return value }
          | }
          | class IntBox(v: Integer) : Box[Integer](v) {
          | public:
          |   def plusOne(): Int {
          |     val g: Int = get()
          |     return g + 1
          |   }
          | }
          | class Main {
          | public:
          |   static def main(args: String[]): String {
          |     return JInteger::toString(new IntBox(41).plusOne())
          |   }
          | }
        """.stripMargin,
        "Main",
        Array()
      )
      assert(Shell.Success("42") == result)
    }

    it("multi-level generic inheritance composes the substitution") {
      val result = shell.run(
        """
          | class Box[T] {
          |   val value: T
          | public:
          |   def this(v: T) { value = v }
          |   def get(): T { return value }
          | }
          | class Mid[U](v: U) : Box[U](v) {
          | public:
          |   def mid(): U { return get() }
          | }
          | class StrBox2(v: String) : Mid[String](v) {
          | public:
          |   def upper(): String {
          |     val g: String = get()
          |     return g.toUpperCase()
          |   }
          | }
          | class Main {
          | public:
          |   static def main(args: String[]): String {
          |     return new StrBox2("world").upper()
          |   }
          | }
        """.stripMargin,
        "Main",
        Array()
      )
      assert(Shell.Success("WORLD") == result)
    }
  }
}
