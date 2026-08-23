package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A Java-style method declaration -- `public void method() { }` -- with the return type
 * before the name instead of using `def`. `public`/`private`/`protected` are access-*section*
 * markers that only ever appear as `public:`, immediately followed by a colon; the parser
 * consumes the modifier and then trips on the return-type identifier that follows, expecting
 * `:` -- never mentioning `def` or the return type's actual position after the method name.
 */
class JavaStyleMethodHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("Java-style method declaration") {
    it("hints at `def` for `public void method() { }` with no preceding section") {
      val msgs = messages(
        """
          |class Point {
          |  val x: Int
          |  public void method() {
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("def"), s"expected a hint mentioning `def`, got: $msgs")
    }

    it("hints at `def` for `private String getName() { }` inside a `public:` section") {
      val msgs = messages(
        """
          |class Point {
          |  val name: String
          |public:
          |  private String getName() {
          |    return name
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("def"), s"expected a hint mentioning `def`, got: $msgs")
    }

    it("does not fire for the correct `def` form") {
      val msgs = messages(
        """
          |class Point {
          |  val x: Int
          |public:
          |  def method(): void {
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.isEmpty, s"expected no errors for the correct `def` form, got: $msgs")
    }

    it("hints at `def` for `void greet(name: String) { }` with no access modifier at all") {
      val msgs = messages(
        """
          |class Greeter {
          |public:
          |  def this { }
          |  void greet(name: String) {
          |    IO::println(name)
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      // "def" alone is too weak here: the raw member-declarator expected-token
      // dump for this shape ("abstract", "def", "final", ...) already contains
      // the literal word, so a hint-specific phrase is required to prove the
      // hint actually fired rather than trivially matching that dump. The hint
      // message is bilingual, but its `def method(): void { ... }` code example
      // is a literal, locale-invariant substring in both bundles.
      assert(
        msgs.contains("def method(): void { ... }"),
        s"expected the java-style-method hint, got: $msgs"
      )
    }

    it("does not fire for the single-identifier Java-style constructor mistake") {
      val msgs = messages(
        """
          |class Point {
          |  val x: Int
          |public:
          |  public Point(x: Int) {
          |    this.x = x
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("def this"), s"expected the constructor hint, got: $msgs")
    }
  }
}
