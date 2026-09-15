package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * `Void` (capitalized) is not Onion's value-less return type -- that is
 * lowercase `void`. Writing `Void` resolves silently to the ordinary
 * reference type `java.lang.Void` (legitimate for Java interop, but only
 * usable via `return null`), so a method declared `(): Void` then reports
 * either E0020 (`return;` with no value) or E0067 (a body that never
 * returns) with no hint that the fix is likely just `void` (lowercase).
 * This is an easy typo coming from Kotlin's `Unit`, C#/Java's boxed `Void`
 * generic, or plain capitalization habit.
 */
class VoidClassReturnHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(e => s"${e.errorCode.getOrElse("")}: ${e.message}")
      case _ => Seq.empty
    }
  }

  describe("a `(): Void` method whose body never returns a value") {
    it("hints that `void` (lowercase) was probably meant") {
      val msgs = messages(
        """
          |class Foo {
          |public:
          |  def bar(): Void {
          |    IO::println("hi")
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("E0067"), s"expected E0067, got: $msgs")
      assert(msgs.contains("void"), s"expected a hint mentioning lowercase `void`, got: $msgs")
    }
  }

  describe("a `(): Void` method using a bare `return;`") {
    it("hints that `void` (lowercase) was probably meant") {
      val msgs = messages(
        """
          |class Foo {
          |public:
          |  def bar(): Void {
          |    return;
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("E0020"), s"expected E0020, got: $msgs")
      assert(msgs.contains("void"), s"expected a hint mentioning lowercase `void`, got: $msgs")
    }
  }

  describe("a correct `(): void` method") {
    it("does not fire the Void hint") {
      val msgs = messages(
        """
          |class Foo {
          |public:
          |  def bar(): void {
          |    IO::println("hi")
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.isEmpty, s"expected no errors, got: $msgs")
    }
  }

  describe("a `(): Void` method that correctly returns null") {
    it("does not fire the Void hint") {
      val msgs = messages(
        """
          |class Foo {
          |public:
          |  def bar(): Void {
          |    return null
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.isEmpty, s"expected no errors, got: $msgs")
    }
  }
}
