package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * `foreach` never parentheses a single loop variable -- `foreach x: Type in xs { ... }` --
 * parentheses are reserved for the two-variable map-destructuring form,
 * `foreach (k, v) in map { ... }`. Writing the Java/Kotlin/JS-style
 * `foreach (x in xs) { ... }` makes the parser take the map-destructuring
 * branch, consume `x` as the key, and then trip on `in` expecting a `,` --
 * which used to surface the unrelated `old_for_in` ("use a C-style loop")
 * hint, even though the fix here is simply to use `foreach` correctly.
 */
class ForeachParensHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("foreach with parenthesized single variable") {
    it("hints at the correct foreach forms instead of the old-style for-in hint") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val xs = [1, 2, 3]
          |    foreach (x in xs) {
          |      IO::println(x)
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("foreach"), s"expected the hint to mention foreach, got: $msgs")
      assert(!msgs.contains("for var i"), s"hint should not fall back to the for-in advice, got: $msgs")
    }

    it("does not fire for the valid two-variable map-destructuring form") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val m = ["a": 1, "b": 2]
          |    foreach (k, v) in m {
          |      IO::println(k)
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.isEmpty, s"expected no errors for valid foreach, got: $msgs")
    }

    it("still hints at a C-style loop for the unrelated old `for x in xs` mistake") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val xs = [1, 2, 3]
          |    for x in xs {
          |      IO::println(x)
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("for var i"), s"expected the old for-in hint to still fire, got: $msgs")
    }
  }
}
