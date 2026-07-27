package onion.compiler.tools

import onion.tools.Shell

/**
 * A `tool` whose parameters cannot all be converted from command-line strings
 * used to make the whole script a silent no-op (issue #424): CLI synthesis
 * bailed out with a bare `return` and, since a `tool`-only script has neither
 * a user `main` nor top-level statements, nothing ever called it. It should
 * instead fail to compile with a diagnostic naming the offending parameter.
 */
class ToolNonCliParamSpec extends AbstractShellSpec {

  describe("a tool with a non-CLI-convertible parameter") {
    it("fails to compile instead of silently compiling to a no-op") {
      val (r, out) = run(
        """record P(x: Int)
          |tool takeRec(p: P): Int requires { console } { IO::println("" + p.x()); return 0 }
          |""".stripMargin)
      assert(Shell.Failure(-1) == r, r.toString)
      assert(out.contains("takeRec"), out)
      assert(out.contains("p"), out)
    }

    it("still derives a CLI when every tool's parameters are CLI-convertible") {
      val (r, out) = run(
        """tool greet(name: String): Int requires { console } { IO::println(name); return 0 }
          |""".stripMargin, "world")
      assert(Shell.Success(0) == r, r.toString)
      assert(out.contains("world"), out)
    }
  }

  private def run(source: String, args: String*): (Shell.Result, String) = {
    val buf = new java.io.ByteArrayOutputStream()
    val ps = new java.io.PrintStream(buf, true, "UTF-8")
    val (savedOut, savedErr) = (System.out, System.err)
    val result =
      try {
        System.setOut(ps); System.setErr(ps)
        Console.withOut(ps) { Console.withErr(ps) {
          shell.run(source, "None", args.toArray)
        }}
      } finally { System.setOut(savedOut); System.setErr(savedErr) }
    (result, new String(buf.toByteArray, "UTF-8"))
  }
}
