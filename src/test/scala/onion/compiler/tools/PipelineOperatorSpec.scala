package onion.compiler.tools

import onion.tools.Shell

/**
 * The |> pipeline operator injects the left value as the first argument of
 * the call on the right: e |> f is f(e), e |> f(a) is f(e, a), and a newline
 * before |> continues the pipeline (one stage per line).
 */
class PipelineOperatorSpec extends AbstractShellSpec {
  describe("|> pipeline") {
    it("pipes into a bare function name and chains") {
      val result = shell.run(
        """
          |def double(x: Int): Int = x * 2
          |IO::println(5 |> double |> double)
          |""".stripMargin,
        "None",
        Array()
      )
      assert(result.isInstanceOf[Shell.Success])
    }

    it("injects as the first argument of a call with arguments") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def add(x: Int, y: Int): Int = x + y
          |  static def main(args: String[]): Int = 5 |> add(3)
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(8) == result)
    }

    it("continues across newlines (one stage per line)") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def trimmed(s: String): String = s.trim()
          |  static def exclaim(s: String): String = s + "!"
          |  static def main(args: String[]): String {
          |    return "  hi  "
          |      |> trimmed
          |      |> exclaim
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("hi!") == result)
    }

    it("mixes with method chains and keeps | and || intact") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def size(xs: List[Integer]): Int = xs.size
          |  static def main(args: String[]): Int {
          |    val bits = 5 | 2
          |    val n = [3, 1, 4].filter { x => x > 1 } |> size
          |    return bits + n
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(9) == result)
    }
  }
}
