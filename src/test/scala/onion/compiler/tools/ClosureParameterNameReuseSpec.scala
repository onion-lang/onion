package onion.compiler.tools

import onion.tools.Shell

/**
 * A lambda parameter is its own variable, whatever outer variable shares its name (#2462).
 * A later local `p` captured by another lambda used to box every `p` in the method, so a
 * lambda parameter `p` read by a nested lambda crashed codegen with I0000.
 */
class ClosureParameterNameReuseSpec extends AbstractShellSpec {
  private def run(body: String): Shell.Result =
    shell.run(
      s"""
         |class Test {
         |public:
         |  static def run(f: (Int) -> String): String = f(1)
         |  static def main(args: String[]): String {
         |$body
         |  }
         |}
         |""".stripMargin,
      "ClosureParameterNameReuse.on",
      Array()
    )

  describe("a lambda parameter read by a nested lambda") {
    it("is not boxed by a later captured val of the same name") {
      val result = run(
        """    val a = run { p -> run { q -> "" + p } }
          |    val p: String = "s"
          |    return a + run { q -> p }""".stripMargin)
      assert(result == Shell.Success("1s"))
    }

    it("is not boxed by an earlier captured var of the same name") {
      val result = run(
        """    var p: String = "s"
          |    val a = run { q -> p = "t"; p }
          |    val b = run { p -> run { q -> "" + p } }
          |    return a + b + p""".stripMargin)
      assert(result == Shell.Success("t1t"))
    }

    it("works three levels deep") {
      val result = run(
        """    val a = run { p -> run { q -> run { r -> "" + p + q + r } } }
          |    val p: String = "s"
          |    return a + run { q -> p }""".stripMargin)
      assert(result == Shell.Success("111s"))
    }
  }

  describe("in a top-level script") {
    it("runs the issue's program") {
      val buf = new java.io.ByteArrayOutputStream()
      val ps = new java.io.PrintStream(buf, true, "UTF-8")
      val o = System.out
      val result =
        try {
          System.setOut(ps)
          Console.withOut(ps) {
            shell.run(
              """
                |def run(f: (Int) -> String): String = f(1)
                |IO::println(run { p -> run { q -> "" + p } })
                |val p: String = "s"
                |IO::println(run { q -> p })
                |""".stripMargin,
              "ClosureParameterNameReuseScript.on",
              Array()
            )
          }
        } finally System.setOut(o)
      val out = new String(buf.toByteArray, "UTF-8")
      assert(result.isInstanceOf[Shell.Success], s"$result\n$out")
      assert(out.linesIterator.toList == List("1", "s"), out)
    }
  }
}
