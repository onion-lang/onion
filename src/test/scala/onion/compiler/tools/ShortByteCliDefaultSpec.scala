package onion.compiler.tools

import onion.tools.Shell

/** Regression test for issue #2506: `s: Short = 3` / `b: Byte = 2` on an auto-CLI `main` or a `tool`. */
class ShortByteCliDefaultSpec extends AbstractShellSpec {

  describe("a Short or Byte parameter with an Int-literal default") {
    it("works on an auto-CLI main, defaulted and overridden") {
      val src =
        """def main(p: Int, s: Short = 3, b: Byte = 2): void {
          |  IO::println("p=" + p + " s=" + s + " b=" + b)
          |}
          |""".stripMargin
      val (r, out) = run(src, "5")
      assert(!r.isInstanceOf[Shell.Failure], out)
      assert(out.contains("p=5 s=3 b=2"), out)
      val (r2, out2) = run(src, "5", "--s", "7")
      assert(!r2.isInstanceOf[Shell.Failure], out2)
      assert(out2.contains("p=5 s=7 b=2"), out2)
    }

    it("works on a tool") {
      val (r, out) = run(
        """tool t(p: Int, s: Short = 3, b: Byte = 2) requires { console } {
          |  IO::println("p=" + p + " s=" + s + " b=" + b)
          |}
          |""".stripMargin, "5")
      assert(Shell.Success(0) == r, out)
      assert(out.contains("p=5 s=3 b=2"), out)
    }
  }

  private def run(source: String, args: String*): (Shell.Result, String) = {
    val buf = new java.io.ByteArrayOutputStream()
    val ps = new java.io.PrintStream(buf, true, "UTF-8")
    val (savedOut, savedErr) = (System.out, System.err)
    val result =
      try {
        System.setOut(ps); System.setErr(ps)
        Console.withOut(ps) { Console.withErr(ps) { shell.run(source, "None", args.toArray) } }
      } finally { System.setOut(savedOut); System.setErr(savedErr) }
    (result, new String(buf.toByteArray, "UTF-8"))
  }
}
