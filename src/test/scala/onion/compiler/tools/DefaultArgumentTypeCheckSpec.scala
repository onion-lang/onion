package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression test for issue #2541: a parameter's default is checked against the parameter's
 * declared type, with the rule `val x: T = e` uses. Before, `b: Byte = 200` compiled and ran
 * as `-56`, `s: Short = 3.7` threw a VerifyError at load, and a widening default such as
 * `l: Long = 7` did too.
 */
class DefaultArgumentTypeCheckSpec extends AbstractShellSpec {

  describe("a default that does not fit its parameter's type") {
    it("is E0000 on an ordinary function, with no second error at the call") {
      val out = compileErrors(
        """def f(p: Int, b: Byte = 200): String = "b=" + b + p
          |IO::println(f(1))
          |""".stripMargin)
      assert(out.contains("E0000"), out)
      assert(!out.contains("E0005"), out)
    }

    it("is E0000 for a fractional default of a Short") {
      val out = compileErrors(
        """def f(p: Int, s: Short = 3.7): String = "s=" + s + p
          |IO::println(f(1))
          |""".stripMargin)
      assert(out.contains("E0000"), out)
    }

    it("is E0000 for a Long default of a Short") {
      val out = compileErrors(
        """def f(s: Short = 70000L): String = "s=" + s
          |IO::println(f())
          |""".stripMargin)
      assert(out.contains("E0000"), out)
    }

    it("is E0000 on an auto-CLI main") {
      val out = compileErrors(
        """def main(p: Int, b: Byte = 200, s: Short = 100000): void { IO::println("b=" + b + " s=" + s + p) }
          |""".stripMargin)
      assert(out.split("E0000", -1).length - 1 == 2, out)
    }

    it("is E0000 on a tool") {
      val out = compileErrors(
        """tool t(b: Byte = 200) requires { console } { IO::println("b=" + b) }
          |""".stripMargin)
      assert(out.contains("E0000"), out)
    }

    it("is E0000 on a method and a constructor") {
      val out = compileErrors(
        """class C {
          |public:
          |  def g(b: Byte = 128): String = "" + b
          |  def this(s: Short = 40000) { }
          |}
          |IO::println(new C().g())
          |""".stripMargin)
      assert(out.split("E0000", -1).length - 1 == 2, out)
    }
  }

  describe("a default that fits its parameter's type") {
    it("still narrows an in-range Int literal and widens a smaller value") {
      val (r, out) = run(
        """def f(b: Byte = -128, s: Short = 32767, c: Char = 'z', l: Long = 7, d: Double = 1.0f, o: Object = 3, n: String? = null): String =
          |  "" + b + "," + s + "," + c + "," + l + "," + d + "," + o + "," + n
          |IO::println(f())
          |""".stripMargin)
      assert(!r.isInstanceOf[Shell.Failure], out)
      assert(out.contains("-128,32767,z,7,1.0,3,null"), out)
    }

    it("still works on an auto-CLI main and a tool") {
      val (r, out) = run("""def main(p: Int, b: Byte = 100, s: Short = -5): void { IO::println("p=" + p + " b=" + b + " s=" + s) }""", "1")
      assert(!r.isInstanceOf[Shell.Failure], out)
      assert(out.contains("p=1 b=100 s=-5"), out)
      val (r2, out2) = run("""tool t(b: Byte = 20, s: Short = 7) requires { console } { IO::println("b=" + b + " s=" + s) }""")
      assert(Shell.Success(0) == r2, out2)
      assert(out2.contains("b=20 s=7"), out2)
    }
  }

  private def compileErrors(source: String): String = {
    val (r, out) = run(source)
    assert(r == Shell.Failure(-1), out)
    out
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
