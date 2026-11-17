package onion.compiler.tools

import onion.tools.Shell

/**
 * #3107: an `if` statement that does not return, right before a member's tail call, must
 * not swallow that tail call. The state machine used to put the tail call in the `if`'s
 * missing `else`, so taking the `then` branch left the state without a transition and the
 * group returned the default value (`null`, `0`, `false`).
 */
class MutualRecursionIfStatementSpec extends AbstractShellSpec {
  private def group(odBody: String, ret: String = "String", seed: String = "\"x\"", n: Int = 4): String =
    s"""
       |class MR {
       |public:
       |  static def main(args: String[]): $ret { return ev($n, $seed) }
       |private:
       |  static @TailRecursive def od(k: Int, s: $ret): $ret {
       |    if k == 0 { return s }
       |$odBody
       |  }
       |  static @TailRecursive def ev(k: Int, s: $ret): $ret {
       |    if k == 0 { return s }
       |    return od(k - 1, s + "e")
       |  }
       |}
       |""".stripMargin

  describe("a @TailRecursive mutual group with a statement before a tail call") {
    it("runs the tail call after an `if` statement that does not return (#3107)") {
      val script = group(
        """    if k == 3 { IO::print("") }
          |    return ev(k - 1, s + "o")""".stripMargin)
      assert(shell.run(script, "MR.on", Array()) == Shell.Success("xeoeo"))
    }

    it("runs the tail call after an `if` statement taken on every round") {
      val script = group(
        """    if k > 0 { IO::print("") }
          |    return ev(k - 1, s + "o")""".stripMargin)
      assert(shell.run(script, "MR.on", Array()) == Shell.Success("xeoeo"))
    }

    it("runs the tail call after an `if` whose branch only changes a parameter") {
      val script = group(
        """    if k == 3 { s = s + "!" }
          |    return ev(k - 1, s + "o")""".stripMargin)
      assert(shell.run(script, "MR.on", Array()) == Shell.Success("xe!oeo"))
    }

    it("still takes an early `return` in the `if` before the tail call") {
      val script = group(
        """    if k == 1 { return s + "<" }
          |    return ev(k - 1, s + "o")""".stripMargin)
      assert(shell.run(script, "MR.on", Array()) == Shell.Success("xeoe<"))
    }

    it("stays stack-safe with the `if` statement on a deep recursion") {
      val script =
        """
          |class MR {
          |public:
          |  static def main(args: String[]): Int { return ev(200000, 0) }
          |private:
          |  static @TailRecursive def od(k: Int, s: Int): Int {
          |    if k == 0 { return s }
          |    if k % 1000 == 1 { IO::print("") }
          |    return ev(k - 1, s + 1)
          |  }
          |  static @TailRecursive def ev(k: Int, s: Int): Int {
          |    if k == 0 { return s }
          |    return od(k - 1, s + 1)
          |  }
          |}
          |""".stripMargin
      assert(shell.run(script, "MR.on", Array()) == Shell.Success(200000))
    }
  }
}
