package onion.compiler

import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * A top-level function lives on a synthetic `<file>Main` class the user never wrote.
 * Diagnostics used to print that owner: E0077 said "calling mainMain::answerText" and
 * E0005 said "mainMain.helper(Int, Int)". They now name the function as written, while
 * a method of a real class keeps its `Class::method` / `Class.method` form.
 *
 * Assertions use error codes and source identifiers, which are the same in both locales.
 */
class TopLevelFunctionDiagnosticNameSpec extends AnyFunSpec {

  private def errors(src: String, file: String = "main.on"): Seq[CompileError] =
    new OnionCompiler(new CompilerConfig(List("."), null, "UTF-8", "", 10))
      .compile(Seq(new StreamInputSource(() => new StringReader(src), file))) match {
      case CompilationOutcome.Failure(errs) => errs
      case other                            => fail(s"expected a compile failure, got $other")
    }

  private def messagesOf(errs: Seq[CompileError], code: String): String = {
    val found = errs.filter(_.errorCode.contains(code))
    assert(found.nonEmpty, s"expected $code, got:\n${errs.map(_.message).mkString("\n")}")
    found.map(_.message).mkString("\n")
  }

  describe("E0077 (undeclared effect)") {
    it("names a top-level callee by its own name (the dogfood report)") {
      val msg = messagesOf(errors(
        """def answerText(p: String): String { return Files::readText(p) }
          |tool answer(p: String): String
          |  requires { console }
          |{
          |  return answerText(p)
          |}
          |""".stripMargin), "E0077")
      assert(msg.contains("answerText"), msg)
      assert(!msg.contains("Main::"), msg)
      assert(!msg.contains("mainMain"), msg)
    }

    it("keeps Class::method for a method of a real class") {
      val msg = messagesOf(errors(
        """class Loader {
          |public:
          |  static def load(p: String): String { return Files::readText(p) }
          |}
          |tool answer(p: String): String
          |  requires { console }
          |{
          |  return Loader::load(p)
          |}
          |""".stripMargin), "E0077")
      assert(msg.contains("Loader::load"), msg)
    }
  }

  describe("E0005 (no applicable method)") {
    it("names a top-level function without its synthetic owner") {
      val msg = messagesOf(errors(
        """def helper(x: Int): Int { return x + 1 }
          |helper(1, 2)
          |val z = helpr(1)
          |""".stripMargin), "E0005")
      assert(msg.contains("helper(Int, Int)"), msg)
      assert(msg.contains("helpr(Int)"), msg)
      assert(!msg.contains("mainMain"), msg)
      assert(!msg.contains("Main."), msg)
    }

    it("keeps Class.method for a method of a real class") {
      val msg = messagesOf(errors(
        """class Box {
          |public:
          |  static def make(x: Int): Int { return x }
          |}
          |Box::make(1, 2)
          |""".stripMargin), "E0005")
      assert(msg.contains("Box.make(Int, Int)"), msg)
    }

    it("has a function-flavoured template in both bundles with no unfilled placeholder") {
      for (bundle <- Seq(MessageBundles.english, MessageBundles.japanese)) {
        val text = java.text.MessageFormat.format(bundle.getString("error.semantic.functionNotFound"), "helper", "Int, Int")
        assert(text.contains("helper(Int, Int)"), text)
        assert(!text.matches("(?s).*\\{\\d+\\}.*"), s"unfilled placeholder: $text")
      }
    }
  }
}
