package onion.compiler.tools

import onion.tools.Shell

/**
 * A file declaring more than one top-level `tool` gets subcommand-style `--help`
 * (issue #2252): the shared `--plan`/`--contract`/`--help` lines are global, not
 * part of whichever tool happens to be declared last, so the output must set them
 * apart instead of running on directly from that tool's own option list.
 */
class MultiToolHelpSpec extends AbstractShellSpec {

  private val script =
    """tool greet(name: String): Int requires { console } {
      |  IO::println(name)
      |  return 0
      |}
      |
      |tool add(a: Int, b: Int): Int requires { console } {
      |  IO::println("" + (a + b))
      |  return 0
      |}
      |""".stripMargin

  describe("--help on a multi-tool file") {
    it("separates the global flags from the last subcommand's own options") {
      val (r, out) = run(script, "--help")
      assert(Shell.Success(0) == r, r.toString)
      val addIdx = out.indexOf("usage:", out.indexOf("usage:") + 1)
      val requiresIdx = out.indexOf("requires: console", addIdx)
      val planIdx = out.indexOf("--plan")
      assert(addIdx >= 0 && requiresIdx >= 0 && planIdx > requiresIdx, out)
      val between = out.substring(requiresIdx, planIdx)
      assert(between.count(_ == '\n') >= 2, s"expected a blank line between `add`'s options and the global flags:\n$out")
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
