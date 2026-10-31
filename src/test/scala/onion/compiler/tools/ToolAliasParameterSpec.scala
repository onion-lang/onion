package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import onion.compiler.toolbox.Message
import onion.tools.Shell

import java.io.StringReader

/**
 * Regression test for issue #2503: a `tool` (or auto-CLI `main`) parameter typed
 * with a `type` alias to a CLI scalar (`type S = String`, `type Port = Int`) was
 * E0081/E0094, because the command-line check read the type as written and never
 * followed the alias. The alias is now resolved first, so it behaves exactly like
 * the type it names; an alias to a list still gets #2490's list-specific E0081.
 */
class ToolAliasParameterSpec extends AbstractShellSpec {

  describe("a tool parameter typed with an alias") {
    it("to String runs like a String parameter") {
      val (r, out) = run(
        """type S = String
          |tool greet(name: S) requires { console } {
          |  IO::println("Hello, " + name)
          |}
          |""".stripMargin, "bob")
      assert(Shell.Success(0) == r, out)
      assert(out.contains("Hello, bob"), out)
    }

    it("to Int, as a defaulted flag, converts the flag value") {
      val (r, out) = run(
        """type Port = Int
          |tool serve(host: String, port: Port = 80) requires { console } {
          |  IO::println(host + ":" + (port + 1))
          |}
          |""".stripMargin, "localhost", "--port", "8080")
      assert(Shell.Success(0) == r, out)
      assert(out.contains("localhost:8081"), out)
    }

    it("through a chain of aliases still resolves") {
      val (r, out) = run(
        """type Count = Int
          |type N = Count
          |tool twice(n: N) requires { console } {
          |  IO::println(n * 2)
          |}
          |""".stripMargin, "21")
      assert(Shell.Success(0) == r, out)
      assert(out.contains("42"), out)
    }

    it("to List[String] gets the list-specific E0081") {
      val result = compile(
        """type Names = List[String]
          |tool greet(names: Names) requires { console } {
          |  IO::println("hi")
          |}
          |""".stripMargin)
      val hit = result.allErrors.find(_.code.contains("E0081"))
      assert(hit.isDefined, s"expected E0081, got: ${result.allErrors.map(e => (e.code, e.message))}")
      val expected = Message("error.semantic.toolListParameter", Array[Any]("greet", "names", "Names"))
      assert(hit.get.message.contains(expected), s"expected '$expected', got: ${hit.get.message}")
    }
  }

  describe("an auto-CLI main parameter typed with an alias") {
    it("to Int converts the argument") {
      val (r, out) = run(
        """type Count = Int
          |def main(n: Count): void {
          |  IO::println(n + 1)
          |}
          |""".stripMargin, "41")
      assert(r.isInstanceOf[Shell.Success], out)
      assert(out.contains("42"), out)
    }
  }

  private def compile(code: String) =
    new OnionCompiler(CompilerConfig(Seq("."), null, "UTF-8", "", 10)).compileDetailed(
      Seq(new StreamInputSource(() => new StringReader(code), "Test.on"))
    )

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
