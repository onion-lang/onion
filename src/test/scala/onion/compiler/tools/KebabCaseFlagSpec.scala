package onion.compiler.tools

import onion.{Cli, Outcome}
import onion.tools.Shell

import scala.jdk.CollectionConverters._

/**
 * A camelCase parameter is reachable by its kebab-case flag too (FRICTION F13):
 * `def main(makeSample: Boolean = false)` used to accept only `--makeSample` and reject
 * `--make-sample` as an unknown option. Both spellings now work, for the `def main`
 * auto-CLI and for `tool` CLIs; usage and `--help` show the kebab-case one; and two
 * parameters that would answer to the same flag are a compile error (E0093).
 *
 * Everything asserted here is either locale-independent output (usage text, ToolCli's
 * English-only messages) or a parameter/flag name that appears verbatim in both
 * locales' compile error.
 */
class KebabCaseFlagSpec extends AbstractShellSpec {

  describe("Cli.kebab") {
    it("splits camelCase words and lower-cases them") {
      assert(Cli.kebab("makeSample") == "make-sample")
      assert(Cli.kebab("outDir") == "out-dir")
      assert(Cli.kebab("maxRowsPerSheet") == "max-rows-per-sheet")
    }

    it("keeps an acronym together and splits the word after it") {
      assert(Cli.kebab("parseURL") == "parse-url")
      assert(Cli.kebab("URLPath") == "url-path")
      assert(Cli.kebab("useHTTPProxy") == "use-http-proxy")
    }

    it("splits after a digit, and leaves names without capitals alone") {
      assert(Cli.kebab("level2Name") == "level2-name")
      assert(Cli.kebab("verbose") == "verbose")
      assert(Cli.kebab("snake_case") == "snake_case")
      assert(Cli.kebab("v2") == "v2")
    }
  }

  describe("def main auto-CLI (onion.Cli)") {
    val spec = "src,makeSample?,outDir="

    it("accepts the kebab-case and the camelCase spelling of a flag") {
      assert(Cli.tryParse(Array("a", "--make-sample", "--out-dir", "o"), spec).get.toList ==
        List("a", "true", "o"))
      assert(Cli.tryParse(Array("a", "--makeSample", "--outDir=o"), spec).get.toList ==
        List("a", "true", "o"))
      assert(Cli.tryParse(Array("a", "--out-dir=o"), spec).get.toList == List("a", null, "o"))
    }

    it("still rejects a spelling that is neither") {
      Cli.tryParse(Array("a", "--make_sample"), spec) match {
        case bad: Outcome.Bad[Array[String]] @unchecked =>
          assert(bad.defects.asScala.exists(_.describe.contains("--make_sample")))
        case other => fail(s"expected a defect, got $other")
      }
    }

    it("shows the kebab-case spelling in usage") {
      Cli.tryParse(Array("--help"), spec) match {
        case bad: Outcome.Bad[Array[String]] @unchecked =>
          val usage = bad.defects.asScala.map(_.describe).mkString("\n")
          assert(usage.contains("[--make-sample]"), usage)
          assert(usage.contains("[--out-dir VALUE]"), usage)
          assert(!usage.contains("--makeSample"), usage)
        case other => fail(s"expected the usage defect, got $other")
      }
    }

    it("binds a kebab-case flag end to end through the synthesized main") {
      val (r, out) = run(
        """def main(makeSample: Boolean = false, outDir: String = "out"): void {
          |  IO::println("makeSample=" + makeSample + " outDir=" + outDir)
          |}
          |""".stripMargin, "--make-sample", "--out-dir", "build")
      assert(r.isInstanceOf[Shell.Success], r.toString + out)
      assert(out.contains("makeSample=true outDir=build"), out)
    }

    it("rejects two parameters that answer to the same flag, at compile time") {
      val (r, out) = run(
        """def main(parseURL: Int = 1, parseUrl: Int = 2): void {
          |  IO::println(parseURL + parseUrl)
          |}
          |""".stripMargin)
      assert(!r.isInstanceOf[Shell.Success], r.toString)
      assert(out.contains("parseURL") && out.contains("parseUrl"), out)
      assert(out.contains("--parse-url"), out)
      assert(out.contains("E0093"), out)
    }

    it("does not report positionals, which have no flag") {
      val (r, out) = run(
        """def main(parseURL: String, parseUrl: String): void {
          |  IO::println(parseURL + parseUrl)
          |}
          |""".stripMargin, "a", "b")
      assert(r.isInstanceOf[Shell.Success], r.toString + out)
      assert(out.contains("ab"), out)
    }
  }

  describe("tool CLIs (onion.ToolCli)") {
    val script =
      """tool gen(outDir: String, makeSample: Boolean = false, maxRows: Int = 10): Int
        |  requires { console }
        |{
        |  IO::println("outDir=" + outDir + " makeSample=" + makeSample + " maxRows=" + maxRows)
        |  return 0
        |}
        |""".stripMargin

    it("accepts the kebab-case spelling of a flag and a switch") {
      val (r, out) = run(script, "d", "--make-sample", "--max-rows", "3")
      assert(Shell.Success(0) == r, r.toString)
      assert(out.contains("outDir=d makeSample=true maxRows=3"), out)
    }

    it("keeps accepting the camelCase spelling") {
      val (r, out) = run(script, "d", "--makeSample", "--maxRows=4")
      assert(Shell.Success(0) == r, r.toString)
      assert(out.contains("outDir=d makeSample=true maxRows=4"), out)
    }

    it("treats the two spellings as one option") {
      val (r, out) = run(script, "d", "--max-rows", "1", "--maxRows", "2")
      assert(Shell.Success(1) == r, r.toString)
      assert(out.contains("more than once"), out)
    }

    it("shows the kebab-case spelling in --help and usage") {
      val (r, out) = run(script, "--help")
      assert(Shell.Success(0) == r, r.toString)
      assert(out.contains("[--make-sample]"), out)
      assert(out.contains("[--max-rows <Int>]"), out)
      assert(out.contains("--max-rows <int>"), out)
      assert(!out.contains("--maxRows"), out)
    }

    it("leaves the contract's parameter names as declared") {
      val (r, out) = run(script, "--contract")
      assert(Shell.Success(0) == r, r.toString)
      assert(out.contains(""""name":"makeSample","type":"Boolean","role":"switch""""), out)
    }

    it("rejects two parameters that answer to the same flag, at compile time") {
      val (r, out) = run(
        """tool t(X: Int = 3, x: Int = 4): Int
          |  requires { console }
          |{
          |  IO::println(X + x)
          |  return 0
          |}
          |""".stripMargin, "--help")
      assert(!r.isInstanceOf[Shell.Success], r.toString)
      assert(out.contains("`X`") && out.contains("`x`") && out.contains("--x"), out)
      assert(out.contains("E0093"), out)
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
