package onion.compiler.tools

import onion.tools.Shell

/**
 * Stage 1 of type classes: `trait` parses (for now) as an interface — it erases
 * to a JVM interface. Instances, [T: C] constraints, and dictionary passing are
 * layered on in later stages.
 */
class TraitParsingSpec extends AbstractShellSpec {
  describe("trait declarations") {
    it("parse and behave as a generic interface a class can implement") {
      val r = shell.run(
        """
          |trait Numeric[T] {
          |  def zero(): T
          |  def plus(a: T, b: T): T
          |}
          |class IntNum <: Numeric[Integer] {
          |public:
          |  def this {}
          |  def zero(): Integer = 0
          |  def plus(a: Integer, b: Integer): Integer { return a + b }
          |}
          |def main(args: String[]): Int {
          |  val n: Numeric[Integer] = new IntNum()
          |  return n.plus(n.zero(), 5)
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(5) == r)
    }
    it("supports default methods on a trait") {
      val r = shell.run(
        """
          |trait Greeter {
          |  def name(): String
          |  def greet(): String { return "Hi " + name() }
          |}
          |class P <: Greeter { public: def this {}
          |  def name(): String { return "Ann" } }
          |def main(args: String[]): String { return new P().greet() }
          |""".stripMargin, "None", Array())
      assert(Shell.Success("Hi Ann") == r)
    }
    it("still allows `trait` as a backtick-escaped identifier") {
      assert(Shell.Success(42) == shell.run(
        "def main(args: String[]): Int { val `trait` = 42\n return `trait` }", "None", Array()))
    }
  }
}
