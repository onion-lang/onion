package onion.compiler.tools

import onion.tools.Shell

/**
 * `--plan` (issue #359): report what a run would do — the declared-and-checked effect
 * set instantiated with the actual argument values — and exit without performing any
 * effect. Honesty rules: an `unknown` effect is said out loud, never omitted, and an
 * operand the analysis cannot tie down is reported as such, never guessed.
 */
class ToolPlanSpec extends AbstractShellSpec {

  private def tmpDir(): java.nio.file.Path =
    java.nio.file.Files.createTempDirectory("onion-plan-spec")

  describe("--plan") {
    it("prints the effects with bound operands and performs none of them") {
      val dir = tmpDir()
      val src = dir.resolve("in.txt"); val dst = dir.resolve("out.txt")
      java.nio.file.Files.writeString(src, "data")
      val (r, out) = run(
        """tool copy(src: String, dst: String): Int
          |  requires { read(src), write(dst) }
          |{
          |  Files::writeText(dst, Files::readText(src))
          |  return 0
          |}
          |""".stripMargin, src.toString, dst.toString, "--plan")
      assert(Shell.Success(0) == r, r.toString)
      assert(out.contains("read") && out.contains("derived from src = " + src.toString), out)
      assert(out.contains("write") && out.contains("derived from dst = " + dst.toString), out)
      assert(out.contains("nothing was executed"), out)
      // The plan performed nothing: the destination does not exist.
      assert(!java.nio.file.Files.exists(dst), s"--plan wrote $dst")
    }

    it("says `unknown` out loud instead of omitting it") {
      val (r, out) = run(
        """import { java.util.Random; }
          |tool roll(): Int
          |  requires { unknown }
          |{
          |  return new Random().nextInt()
          |}
          |""".stripMargin, "--plan")
      assert(Shell.Success(0) == r, r.toString)
      assert(out.contains("unknown"), out)
      assert(out.contains("cannot characterize"), out)
    }

    it("shows the contract default for an operand left to its default") {
      val (r, out) = run(
        """tool fetch(url: String = "https://example.com"): Int
          |  requires { net(url) }
          |{
          |  IO::println(Http::get(url))
          |  return 0
          |}
          |""".stripMargin, "--plan")
      // console is undeclared above — so this must fail to compile...
      assert(r.isInstanceOf[Shell.Failure], r.toString)
    }

    it("shows defaults and ambient effects honestly") {
      val (r, out) = run(
        """tool fetch(url: String = "https://example.com"): Int
          |  requires { net(url), console }
          |{
          |  IO::println(Http::get(url))
          |  return 0
          |}
          |""".stripMargin, "--plan")
      assert(Shell.Success(0) == r, r.toString)
      assert(out.contains("net") && out.contains("derived from url = https://example.com") && out.contains("default"), out)
      // Ambient effect: named bare, no bogus operand.
      assert(out.linesIterator.exists(_.trim == "console"), out)
      // And no network call happened — the body would have printed the response.
      assert(!out.contains("<html"), out)
    }

    it("still reports a parse error rather than planning nonsense") {
      val (r, out) = run(
        """tool count(n: Int): Int requires { console } { IO::println("" + n); return 0 }
          |""".stripMargin, "abc", "--plan")
      assert(Shell.Success(1) == r, r.toString)
      assert(out.contains("not a valid Int"), out)
    }

    it("is listed in --help") {
      val (r, out) = run(
        """tool t(): Int { return 0 }
          |""".stripMargin, "--help")
      assert(Shell.Success(0) == r, r.toString)
      assert(out.contains("--plan"), out)
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
