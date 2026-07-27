package onion.compiler.tools

import onion.tools.Shell

/**
 * The tool CLI is derived from the machine-readable contract (issue #358): one
 * declaration yields `--contract` (what an agent reads), `--help` with types and
 * defaults, typed parsing, and exit codes instead of `System.exit` — which is why
 * every case here runs in-process.
 */
class ToolContractCliSpec extends AbstractShellSpec {

  private val script =
    """tool greet(name: String, count: Int = 2, loud: Boolean = false): Int
      |  requires { console }
      |{
      |  var i = 0
      |  while i < count {
      |    if loud { IO::println(name.toUpperCase()) } else { IO::println(name) }
      |    i = i + 1
      |  }
      |  return 0
      |}
      |""".stripMargin

  describe("--contract") {
    it("prints the machine-readable contract: params, types, roles, defaults, capabilities") {
      val (r, out) = run(script, "--contract")
      assert(Shell.Success(0) == r, r.toString)
      assert(out.contains(""""tool":"greet""""), out)
      assert(out.contains(""""name":"name","type":"String","role":"positional""""), out)
      assert(out.contains(""""name":"count","type":"Int","role":"flag","default":"2""""), out)
      assert(out.contains(""""name":"loud","type":"Boolean","role":"switch","default":"false""""), out)
      assert(out.contains(""""capabilities":["console"]"""), out)
      assert(out.contains(""""returns":"Int""""), out)
    }
  }

  describe("--help") {
    it("describes every argument with its type and default, and the capabilities") {
      val (r, out) = run(script, "--help")
      assert(Shell.Success(0) == r, r.toString)
      assert(out.contains("<name>"), out)
      assert(out.contains("--count <int>"), out)
      assert(out.contains("(default: 2)"), out)
      assert(out.contains("--loud"), out)
      assert(out.contains("requires: console"), out)
      assert(out.contains("--contract"), out)
    }
  }

  describe("parsing") {
    it("binds positionals, flags and switches, and runs the tool") {
      val (r, out) = run(script, "world", "--count", "1", "--loud")
      assert(Shell.Success(0) == r, r.toString)
      assert(out.contains("WORLD"), out)
    }

    it("accepts the --name=value form and evaluates absent defaults in-language") {
      val (r, out) = run(script, "hi", "--count=3")
      assert(Shell.Success(0) == r, r.toString)
      assert(out.linesIterator.count(_ == "hi") == 3, out)
    }

    it("reports a missing positional by name and type, with exit code 1") {
      val (r, out) = run(script)
      assert(Shell.Success(1) == r, r.toString)
      assert(out.contains("<name: String>"), out)
      assert(out.contains("usage:"), out)
    }

    it("reports a bad value naming the option and the expected type") {
      val (r, out) = run(script, "x", "--count", "many")
      assert(Shell.Success(1) == r, r.toString)
      assert(out.contains("--count"), out)
      assert(out.contains("Int"), out)
    }

    it("rejects an unknown option") {
      val (r, out) = run(script, "x", "--nope")
      assert(Shell.Success(1) == r, r.toString)
      assert(out.contains("--nope"), out)
    }

    it("rejects a flag given more than once instead of silently taking the last value") {
      val (r, out) = run(script, "x", "--count", "1", "--count", "2")
      assert(Shell.Success(1) == r, r.toString)
      assert(out.contains("--count"), out)
      assert(out.contains("more than once"), out)
    }

    it("rejects a switch given more than once") {
      val (r, out) = run(script, "x", "--loud", "--loud")
      assert(Shell.Success(1) == r, r.toString)
      assert(out.contains("--loud"), out)
      assert(out.contains("more than once"), out)
    }
  }

  describe("several tools in one script") {
    val multi =
      """tool add(a: Int, b: Int): Int requires { console } { IO::println("" + (a + b)); return 0 }
        |tool mul(a: Int, b: Int): Int requires { console } { IO::println("" + (a * b)); return 0 }
        |""".stripMargin

    it("dispatches on the first argument as a subcommand") {
      val (r1, out1) = run(multi, "add", "20", "22")
      assert(Shell.Success(0) == r1, r1.toString)
      assert(out1.contains("42"), out1)
      val (r2, out2) = run(multi, "mul", "6", "7")
      assert(Shell.Success(0) == r2, r2.toString)
      assert(out2.contains("42"), out2)
    }

    it("lists the tools when the subcommand is unknown") {
      val (r, out) = run(multi, "div", "1", "2")
      assert(Shell.Success(1) == r, r.toString)
      assert(out.contains("add") && out.contains("mul"), out)
    }

    it("emits one contract entry per tool") {
      val (r, out) = run(multi, "--contract")
      assert(Shell.Success(0) == r, r.toString)
      assert(out.contains(""""tool":"add"""") && out.contains(""""tool":"mul""""), out)
    }
  }

  describe("synthesis stays out of the way") {
    it("does not fire when the script has its own main") {
      val own = script +
        """def main(): void { IO::println("own main") }
          |""".stripMargin
      val (r, out) = run(own)
      assert(r.isInstanceOf[Shell.Success], r.toString) // void main: value is not 0
      assert(out.contains("own main"), out)
    }

    it("does not fire when the script has top-level statements") {
      val stmts = script + "\nIO::println(\"script body\")\n"
      val (r, out) = run(stmts)
      assert(r.isInstanceOf[Shell.Success], r.toString)
      assert(out.contains("script body"), out)
    }
  }

  /** Compiles and runs in-process, capturing both streams. */
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
