package onion.compiler.tools

import onion.tools.Shell

class MutualRecursionOverloadSpec extends AbstractShellSpec {
  describe("@TailRecursive mutual recursion with an overload outside the group") {
    it("keeps the overload a plain call instead of jumping into the group member") {
      val script =
        """
          |class M {
          |private:
          |  static @TailRecursive def a(n: Int): String {
          |    if n == 0 { return "A0" }
          |    return b(n - 1)
          |  }
          |  static def a(s: String): String = "other:" + s
          |  static @TailRecursive def b(n: Int): String {
          |    if n == 0 { return a("x") }
          |    return a(n - 1)
          |  }
          |public:
          |  static def main(args: String[]): String = a(3) + "," + a(4)
          |}
          |""".stripMargin

      val result = shell.run(script, "MutualRecursionOverload.on", Array())
      assert(result == Shell.Success("other:x,A0"), s"unexpected $result")
    }
  }
}
