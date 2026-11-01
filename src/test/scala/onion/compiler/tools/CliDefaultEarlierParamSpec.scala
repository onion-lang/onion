package onion.compiler.tools

import onion.tools.Shell

/** Regression test for issue #2575: an auto-CLI `main` or `tool` default that reads an earlier parameter. */
class CliDefaultEarlierParamSpec extends AbstractShellSpec {

  describe("a default that reads an earlier parameter") {
    it("works on an auto-CLI main, defaulted and overridden") {
      val src = "def main(a: Int, b: Int = a * 2): void { IO::println(\"sum=\" + (a + b)) }\n"
      val (r, out) = run(src, "5")
      assert(!r.isInstanceOf[Shell.Failure], out)
      assert(out.contains("sum=15"), out)
      val (r2, out2) = run(src, "5", "--b", "1")
      assert(!r2.isInstanceOf[Shell.Failure], out2)
      assert(out2.contains("sum=6"), out2)
    }

    it("works on a tool") {
      val src =
        """tool scale(a: Int, b: Int = a * 2) requires { console } {
          |  IO::println("sum=" + (a + b))
          |}
          |""".stripMargin
      val (r, out) = run(src, "5")
      assert(Shell.Success(0) == r, out)
      assert(out.contains("sum=15"), out)
      val (r2, out2) = run(src, "5", "--b", "1")
      assert(Shell.Success(0) == r2, out2)
      assert(out2.contains("sum=6"), out2)
    }

    it("reads the parameter, not the script's String[] args") {
      val (r, out) = run(
        "def main(args: String, out: String = args + \".out\"): void { IO::println(\"out=\" + out) }\n",
        "in.txt")
      assert(!r.isInstanceOf[Shell.Failure], out)
      assert(out.contains("out=in.txt.out"), out)
    }

    it("evaluates each default once, in order, seeing earlier defaults") {
      val (r, out) = run(
        """var count: Int = 0
          |def tick(): Int { count = count + 1; return count * 10 }
          |def main(a: Int, b: Int = tick(), c: Int = b + a, name: String = "n" + c): void {
          |  IO::println("a=#{a} b=#{b} c=#{c} name=#{name} ticks=#{count}")
          |}
          |""".stripMargin, "1")
      assert(!r.isInstanceOf[Shell.Failure], out)
      assert(out.contains("a=1 b=10 c=11 name=n11 ticks=1"), out)
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
