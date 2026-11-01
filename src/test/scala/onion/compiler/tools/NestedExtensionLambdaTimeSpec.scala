package onion.compiler.tools

import onion.tools.Shell

/** Nested lambdas with declared parameters, passed to an overloaded user extension, compile in
 *  time linear in their depth (#2463). Depth 26 took minutes before. */
class NestedExtensionLambdaTimeSpec extends AbstractShellSpec {

  describe("nested lambdas passed to an overloaded extension") {
    it("compile in time linear in the nesting depth") {
      val depth = 26
      val inner = (depth to 1 by -1).foldLeft("s" + depth) { (body, i) =>
        "\"a\".w((s" + i + ": String) -> " + body + ")"
      }
      val src =
        "extension String {\n" +
        "  def w(o: Object): String = \"o\"\n" +
        "  def w(f: (String) -> String): String = f(\"x\")\n" +
        "}\n" +
        "class Test {\npublic:\n  static def main(args: String[]): Int {\n" +
        "    return " + inner + ".length()\n  }\n}\n"
      val start = System.nanoTime()
      assert(shell.run(src, "NestedExtLambdaTime.on", Array()) == Shell.Success(1))
      val seconds = (System.nanoTime() - start) / 1e9
      assert(seconds < 30.0, s"took $seconds s")
    }
  }
}
