package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A Java-style `@Override` annotation on a method that also uses Java's
 * before-the-name return type and no `def` -- `@Override void method() { }`.
 * Onion's `@Name` token is real annotation syntax (`anns=annotations() "def" ...`),
 * so `@Override` alone parses fine; the parser only trips on the return-type
 * identifier that follows it, at member-declarator position, with a raw modifier-
 * keyword dump that never mentions `def` or Onion's actual override syntax --
 * the `override` modifier keyword, not an annotation.
 */
class JavaStyleOverrideAnnotationHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("Java-style @Override annotation") {
    it("hints at the `override` modifier for `@Override void method() { }`") {
      val msgs = messages(
        """
          |class Base {
          |public:
          |  def greet(): void { }
          |}
          |class Point extends Base {
          |public:
          |  @Override void greet() {
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(
        msgs.contains("override def method(): void { ... }"),
        s"expected the override-annotation hint, got: $msgs"
      )
    }

    it("hints at `def` for a differently-named annotation, `@Deprecated void method() { }`") {
      val msgs = messages(
        """
          |class Point {
          |public:
          |  @Deprecated void method() {
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("def"), s"expected a hint mentioning `def`, got: $msgs")
    }

    it("does not fire for the correct `override def` form") {
      val msgs = messages(
        """
          |class Base {
          |public:
          |  def greet(): void { }
          |}
          |class Point extends Base {
          |public:
          |  override def greet(): void { }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.isEmpty, s"expected no errors for the correct `override def` form, got: $msgs")
    }

    it("does not fire for the correct `@Override def` annotation form") {
      val msgs = messages(
        """
          |class Base {
          |public:
          |  def greet(): void { }
          |}
          |class Point extends Base {
          |public:
          |  @Override def greet(): void { }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.isEmpty, s"expected no errors for the correct `@Override def` form, got: $msgs")
    }
  }
}
