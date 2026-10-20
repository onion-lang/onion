package onion.compiler.tools

import onion.{Cli, Outcome}
import onion.tools.Shell
import scala.jdk.CollectionConverters.*

/**
 * Passing a positional parameter as a flag (`greet --name kota` for `tool greet(name:
 * String)`) said only "unknown option `--name`", though `--name` is exactly the
 * parameter's name. The error now says the parameter is positional and is passed without
 * the flag, for `tool` CLIs and the `def main` auto-CLI alike. Messages are English-only.
 */
class PositionalAsFlagHintSpec extends AbstractShellSpec {

  describe("a tool CLI (onion.ToolCli)") {
    val script =
      """tool greet(userName: String, times: Int = 1): Int
        |  requires { console }
        |{
        |  IO::println("hi " + userName)
        |  return 0
        |}
        |""".stripMargin

    it("says a flag named after a positional parameter is positional") {
      val (r, out) = run(script, "--userName", "kota")
      assert(Shell.Success(1) == r, r.toString)
      assert(out.contains("`userName` is positional"), out)
    }
    it("recognizes the kebab-case spelling too") {
      val (_, out) = run(script, "--user-name", "kota")
      assert(out.contains("`userName` is positional"), out)
    }
    it("keeps the plain message for a name that is no parameter") {
      val (_, out) = run(script, "kota", "--nope")
      assert(out.contains("unknown option `--nope`") && !out.contains("is positional"), out)
    }
  }

  describe("the def main auto-CLI (onion.Cli)") {
    it("says a flag named after a positional parameter is positional") {
      Cli.tryParse(Array("--src", "a"), "src,verbose?") match {
        case bad: Outcome.Bad[Array[String]] @unchecked =>
          assert(bad.defects.asScala.exists(_.describe.contains("`src` is positional")), bad.defects.asScala.map(_.describe))
        case other => fail(s"expected a defect, got $other")
      }
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
