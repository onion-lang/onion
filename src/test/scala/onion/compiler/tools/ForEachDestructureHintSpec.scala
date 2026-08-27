package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A Java/Python/JS-style `for (a, b) in xs { ... }` (or unparenthesized
 * `for a, b in xs { ... }`) loop, meant to destructure map entries or iterate
 * a collection. Onion's `for` never destructures or iterates directly -- that
 * is `foreach`'s job -- but the comma inside the parens makes the parser trip
 * on it while still trying to read a C-style `for (init; cond; step)` clause,
 * so without a dedicated hint the line falls through to the generic
 * C-style-`for` hint, which recommends a numeric-index rewrite that has
 * nothing to do with the actual mistake.
 */
class ForEachDestructureHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("`for (a, b) in xs` destructuring mistake") {
    it("hints at `foreach (k, v) in m` for the parenthesized form") {
      val msgs = messages(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val m = ["a": 1, "b": 2]
          |    for (k, v) in m {
          |      IO::println(k)
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("foreach (k, v) in m"), s"expected a hint mentioning `foreach (k, v) in m`, got: $msgs")
    }

    it("hints at `foreach (k, v) in m` for the unparenthesized form") {
      val msgs = messages(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val m = ["a": 1, "b": 2]
          |    for k, v in m {
          |      IO::println(k)
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("foreach (k, v) in m"), s"expected a hint mentioning `foreach (k, v) in m`, got: $msgs")
    }

    it("does not fire for the correct `foreach (k, v) in m` form") {
      val msgs = messages(
        """
          |class Test {
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
      assert(msgs.isEmpty, s"expected no errors for the correct `foreach` form, got: $msgs")
    }

    it("still hints at the C-style rewrite for a real `for (init; cond; step)` loop") {
      val msgs = messages(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    for (int i = 0; i < 10; i++) {
          |      IO::println(i)
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("for var i"), s"expected the C-style `for` hint to still fire, got: $msgs")
    }
  }
}
