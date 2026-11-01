package onion.compiler.tools

import onion.tools.Shell

/**
 * An untyped lambda passed to overloaded class methods prefers a function type over a SAM
 * interface, as for extension methods (rule 3 on #2447, #2472).
 */
class ClassOverloadLambdaSpec extends AbstractShellSpec {
  private def runMain(decls: String, body: String): Shell.Result =
    shell.run(
      s"""
         |import { java.util.function.Function; java.util.Comparator }
         |$decls
         |class Main {
         |public:
         |  static def main(args: String[]): String {
         |    return $body
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  describe("an untyped lambda argument to overloaded class methods") {
    it("picks the function type over a SAM interface") {
      val result = runMain(
        """class K {
          |public:
          |  def this { }
          |  def outer(f: (Int) -> Int): String = "oi:" + f(1)
          |  def outer(f: Function[String, String]): String = "os:" + f.apply("s")
          |}""".stripMargin,
        "new K().outer { p -> ((q: Int) -> p).call(1) }"
      )
      assert(Shell.Success("oi:1") == result)
    }

    it("picks the function type over Comparator for a two-parameter lambda") {
      val result = runMain(
        """class K {
          |public:
          |  def this { }
          |  def o(f: (String, String) -> Int): String = "fn"
          |  def o(c: Comparator[String]): String = "cmp"
          |}""".stripMargin,
        "new K().o { a, b -> 0 }"
      )
      assert(Shell.Success("fn") == result)
    }

    it("does the same for static methods") {
      val result = runMain(
        """class K {
          |public:
          |  static def so(f: (Int) -> Int): String = "oi"
          |  static def so(f: Function[String, String]): String = "os"
          |}""".stripMargin,
        "K::so { p -> ((q: Int) -> p).call(1) }"
      )
      assert(Shell.Success("oi") == result)
    }
  }
}
