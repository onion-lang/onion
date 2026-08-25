package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * `SyntaxHintClassifier` looks up some hints via the zero-argument
 * `Message(property)`, which returns the resource bundle string as-is — it never runs
 * `MessageFormat`. A property text written with MessageFormat's quoting convention
 * (`'{'`/`'}'` to escape a literal brace) is therefore never unescaped, so the single
 * quotes leak into the message the user sees instead of the brace they were meant to
 * protect. Only properties that are looked up through an *args*-taking `Message(...)`
 * overload actually go through `MessageFormat.format`, where that quoting is required.
 */
class HintMessageFormatSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("hints resolved via the zero-argument Message lookup") {
    it("renders literal braces for the ternary hint, not quoted placeholders") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val y = (1 > 0) ? 1 : 0
          |    return y
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(!msgs.contains("'{'") && !msgs.contains("'}'"), s"hint leaked MessageFormat quoting: $msgs")
      assert(msgs.contains("if cond { a } else { b }"), s"expected literal braces in the hint, got: $msgs")
    }

    it("renders literal braces for the old-trailing-arrow hint, not quoted placeholders") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val xs = [1, 2, 3].filter { x => x > 1 }
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(!msgs.contains("'{'") && !msgs.contains("'}'"), s"hint leaked MessageFormat quoting: $msgs")
      assert(msgs.contains("{ x -> ... }"), s"expected literal braces in the hint, got: $msgs")
    }

    it("renders literal braces for the switch-statement hint, not quoted placeholders") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    switch (1) {
          |    case 1: IO::println("one")
          |    else: IO::println("other")
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(!msgs.contains("'{'") && !msgs.contains("'}'"), s"hint leaked MessageFormat quoting: $msgs")
      assert(msgs.contains("select value { case 1: ...; else: ... }"), s"expected literal braces in the hint, got: $msgs")
    }

    it("renders a literal brace for the dangling-else hint, not a quoted placeholder") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    else {
          |      IO::println("b")
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(!msgs.contains("'{'") && !msgs.contains("'}'"), s"hint leaked MessageFormat quoting: $msgs")
      assert(msgs.contains("`}`"), s"expected a literal closing brace in the hint, got: $msgs")
    }
  }

  describe("hints resolved via the args-taking Message lookup") {
    it("renders the python-style-return-arrow hint without crashing on the apostrophe in the pattern text") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  def bar(x: Int) -> Int {
          |    ret x
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      // Locale-independent only: the hint text itself is translated (ja), but the
      // MessageFormat pattern escaping (and the apostrophe bug it must not regress to)
      // is shared by both bundles, and the quoted code snippet is never translated.
      assert(!msgs.contains("I0000"), s"hint crashed MessageFormat: $msgs")
      assert(!msgs.contains("'{'") && !msgs.contains("'}'"), s"hint leaked MessageFormat quoting: $msgs")
      assert(!msgs.contains("''"), s"hint leaked an unresolved doubled-quote escape: $msgs")
      assert(msgs.contains("def bar(...): Int { ... }"), s"expected substituted literal braces in the hint, got: $msgs")
    }
  }
}
