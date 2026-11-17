package onion.compiler.tools

import onion.tools.Shell

/**
 * An untyped lambda passed to a constructor whose later parameters take their defaults is
 * typed against that constructor's parameter, as the same call on a method is (#2919).
 */
class ConstructorLambdaDefaultSpec extends AbstractShellSpec {
  describe("a lambda argument with a defaulted constructor parameter") {
    it("types the lambda against the only candidate") {
      val result = shell.run(
        """
          |class A {
          |public:
          |  val r: Int
          |  def this(f: Int -> Int, k: Int = 1) { this.r = f(k) }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return "" + new A(x -> x * 2).r + "," + new A(x -> x * 2, 5).r
          |  }
          |}
          |""".stripMargin, "ld.on", Array[String]())
      assert(Shell.Success("2,10") == result)
    }
    it("types a lambda that follows another argument") {
      val result = shell.run(
        """
          |class A {
          |public:
          |  val r: Int
          |  def this(n: Int, f: Int -> Int, k: Int = 1) { this.r = n + f(k) }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "" + new A(10, x -> x * 2).r }
          |}
          |""".stripMargin, "ld2.on", Array[String]())
      assert(Shell.Success("12") == result)
    }
  }
}
