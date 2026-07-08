package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression coverage for issue #312: F-bounded self-inheritance (the CRTP /
 * self-builder pattern) must compile for user-defined generics, while a
 * genuinely bound-violating type argument must still be rejected.
 *
 * The self relation (`Sub IS-A Base[Sub]`) is only established once Sub's
 * supertype chain is set, so the type-argument bound check on the supertype
 * reference is deferred until the chain is in place; see
 * TypingOutlinePass.constructTypeHierarchy / TypingTypeSupport.validateTypeApplication.
 */
class FBoundedSelfTypeSpec extends AbstractShellSpec {
  describe("F-bounded self types (CRTP)") {
    it("compiles and runs a bounded self-inheriting class: class Sub : Base[Sub]") {
      val result = shell.run(
        """
          | class Base[T extends Base[T]] {
          | public:
          |   def this { }
          |   def who(): String { return "Base" }
          | }
          | class Sub : Base[Sub] {
          | public:
          |   def this { }
          | }
          | class Main {
          | public:
          |   static def main(args: String[]): String {
          |     val s: Sub = new Sub()
          |     return s.who()
          |   }
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("Base") == result)
    }

    it("compiles and runs a bounded self-referential interface: interface Cmp[T extends Cmp[T]]") {
      val result = shell.run(
        """
          | interface Cmp[T extends Cmp[T]] {
          |   def to(o: T): Int
          | }
          | class Item <: Cmp[Item] {
          | public:
          |   def this { }
          |   def to(o: Item): Int = 0
          | }
          | class Main {
          | public:
          |   static def main(args: String[]): String {
          |     val a: Item = new Item()
          |     val b: Item = new Item()
          |     return JInteger::toString(a.to(b))
          |   }
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("0") == result)
    }

    it("rejects a bound-violating type argument: class Bad : Base[String]") {
      val result = shell.run(
        """
          | class Base[T extends Base[T]] {
          | public:
          |   def this { }
          | }
          | class Bad : Base[String] {
          | public:
          |   def this { }
          | }
          | class Main {
          | public:
          |   static def main(args: String[]): String { return "x" }
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }
  }
}
