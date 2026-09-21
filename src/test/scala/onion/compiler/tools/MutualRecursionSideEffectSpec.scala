package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression test for the @TailRecursive mutual recursion optimizer bug
 * where parameters used inside AsInstanceOf nodes (produced by string
 * concatenation's String.valueOf wrapping) were not rewritten to loop
 * variables, causing side effects before tail calls to always see the
 * original (initial) parameter value instead of the current accumulated value.
 */
class MutualRecursionSideEffectSpec extends AbstractShellSpec {
  describe("@TailRecursive mutual recursion with side effects") {
    it("reads the current accumulated string parameter (not the original) in side effects before the tail call") {
      val script =
        """
          |class MutRec {
          |  @TailRecursive
          |  def stateA(n: Int, acc: String, result: List[String]): List[String] {
          |    if n <= 0 {
          |      result.add("A:" + acc)
          |      return result
          |    }
          |    result.add("step-A:" + acc)
          |    return stateB(n - 1, acc + "x", result)
          |  }
          |
          |  @TailRecursive
          |  def stateB(n: Int, acc: String, result: List[String]): List[String] {
          |    if n <= 0 {
          |      result.add("B:" + acc)
          |      return result
          |    }
          |    result.add("step-B:" + acc)
          |    return stateA(n - 1, acc + "y", result)
          |  }
          |
          |  def run(n: Int): List[String] = stateA(n, "", [])
          |
          |public:
          |  static def main(args: String[]): String {
          |    val m: MutRec = new MutRec()
          |    val result: List[String] = m.run(4)
          |    var joined: String = ""
          |    foreach s: String in result {
          |      if joined.length() > 0 { joined = joined + "|" }
          |      joined = joined + s
          |    }
          |    return joined
          |  }
          |}
        """.stripMargin

      // With the bug: acc reads original JVM param slot ("") in every iteration, giving:
      //   "step-A:|step-B:|step-A:|step-B:|A:"
      // With the fix: acc correctly reads the loop variable, giving:
      //   "step-A:|step-B:x|step-A:xy|step-B:xyx|A:xyxy"
      val result = shell.run(script, "MutualRecursionSideEffect.on", Array())
      assert(result == Shell.Success("step-A:|step-B:x|step-A:xy|step-B:xyx|A:xyxy"))
    }
  }
}
