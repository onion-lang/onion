package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A Java-style annotated method, `@Override void method() { ... }`, that keeps Java's
 * `@Override` but drops `def` and puts the return type before the name -- the two
 * mistakes an actual Java override almost always brings together. Onion's annotations
 * are real syntax (`@Override def method(): void { ... }` is valid) and `annotations()`
 * consumes `@Override` without complaint, but the grammar right after it requires the
 * literal `def` token -- so when a Java-style declaration follows instead, the parser
 * trips right back on the `@Override` token itself, with an expected-token dump listing
 * `modifiers()`'s keywords (`abstract`, `final`, `internal`, `override`, ...) that never
 * mentions `def` -- even though the annotation itself is not the problem.
 */
class JavaStyleAnnotatedMethodHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("Java-style annotated method declaration") {
    it("hints at `def` for `@Override void method() { ... }` on one line") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  @Override void method() { IO::println("x") }
          |  static def main(args: String[]): Int { return 0 }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("def"), s"expected a hint mentioning def, got: $msgs")
      assert(msgs.contains("@Override"), s"expected the hint to name the annotation, got: $msgs")
    }

    it("hints at `def` for `@Override` on its own line above a Java-style method") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  @Override
          |  void method() { IO::println("x") }
          |  static def main(args: String[]): Int { return 0 }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("def"), s"expected a hint mentioning def, got: $msgs")
      assert(msgs.contains("@Override"), s"expected the hint to name the annotation, got: $msgs")
    }

    it("names a different annotation correctly, `@Deprecated String getName() { ... }`") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  @Deprecated String getName() { return "x" }
          |  static def main(args: String[]): Int { return 0 }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("@Deprecated"), s"expected the hint to name @Deprecated, got: $msgs")
    }

    it("does not fire for a correctly-annotated method, `@Override def toString(): String { ... }`") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  @Override
          |  def toString(): String { return "x" }
          |  static def main(args: String[]): Int { return 0 }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(!msgs.contains("@Override"), s"correct code should not raise any error, got: $msgs")
    }
  }
}
