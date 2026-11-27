package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A top-level break/continue followed by another statement must report the real
 * cause (E0048/E0049, break/continue outside a loop), not a misleading syntax
 * error on the next line. The parser used to let break/continue swallow the
 * following line's identifier as a label.
 */
class BreakContinueDiagnosticSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  describe("break/continue outside a loop diagnostics") {
    it("reports E0048 for a top-level break followed by a statement") {
      assert(errorCodes("break\nIO::println(\"end\")\n").contains("E0048"))
    }
    it("reports E0049 for a top-level continue followed by a statement") {
      assert(errorCodes("continue\nIO::println(\"end\")\n").contains("E0049"))
    }
    it("reports E0048 across a blank line too") {
      assert(errorCodes("break\n\nIO::println(\"end\")\n").contains("E0048"))
    }
    it("reports E0048 for a labeled break at top level") {
      assert(errorCodes("break outer\nIO::println(\"end\")\n").contains("E0048"))
    }
    it("reports E0048 for a break inside a closure body nested in a for loop") {
      val src =
        """class C {
          |public:
          |  static def main(args: String[]): void {
          |    for var i: Int = 0; i < 3; i = i + 1 {
          |      val f: () -> Int = () -> { break; return 0 }
          |      IO::println("" + f.call())
          |    }
          |  }
          |}
          |""".stripMargin
      assert(errorCodes(src).contains("E0048"))
    }
    it("reports E0049 for a continue inside a closure body nested in a while loop") {
      val src =
        """class C {
          |public:
          |  static def main(args: String[]): void {
          |    var i: Int = 0;
          |    while i < 3 {
          |      val f: () -> Int = () -> { continue; return 0 }
          |      IO::println("" + f.call())
          |      i = i + 1
          |    }
          |  }
          |}
          |""".stripMargin
      assert(errorCodes(src).contains("E0049"))
    }
  }

  // #3113: a lambda passed to a call is typed with errors muted and the result kept, so the
  // error was lost and the `break` became a no-op that made the lambda return null.
  describe("break/continue in a lambda passed to a call (#3113)") {
    val call = "def call(f: () -> String): String = f()\n"
    def codes(src: String): Seq[String] = errorCodes(src)

    it("reports E0048 for a trailing lambda inside a foreach") {
      val result = codes(call + "foreach i in 1..3 { println(call { -> break }) }\nprintln(\"end\")\n")
      assert(result == Seq("E0048"), result)
    }
    it("reports E0048 for a trailing lambda with no loop at all") {
      val result = codes(call + "println(call { -> break })\n")
      assert(result == Seq("E0048"), result)
    }
    it("reports E0049 for continue in a parenthesized lambda argument") {
      val result = codes(call + "foreach i in 1..3 { println(call({ -> continue })) }\n")
      assert(result == Seq("E0049"), result)
    }
    it("reports E0048 once for a break that is not the lambda's last statement") {
      val result = codes(call + "println(call { ->\n  if true { break }\n  \"x\"\n})\n")
      assert(result == Seq("E0048"), result)
    }
    it("reports E0048 for a lambda argument of a Java method") {
      val result = codes("val xs = [1, 2, 3]\nxs.forEach { x -> if x == 2 { break } }\n")
      assert(result == Seq("E0048"), result)
    }
    it("still runs a loop inside the lambda that breaks out of itself") {
      val src =
        """class C {
          |public:
          |  static def call(f: () -> String): String = f()
          |  static def main(args: String[]): String {
          |    var out = ""
          |    foreach i in 1..2 {
          |      out = out + call { ->
          |        var n = 0
          |        while true {
          |          n += 1
          |          if n > 2 { break }
          |          continue
          |        }
          |        "n=" + n + ";"
          |      }
          |    }
          |    return out
          |  }
          |}
          |""".stripMargin
      assert(codes(src).isEmpty)
      assert(shell.run(src, "None", Array()) == onion.tools.Shell.Success("n=3;n=3;"))
    }
  }
}
