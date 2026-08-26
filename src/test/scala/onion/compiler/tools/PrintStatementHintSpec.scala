package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A Python 2-style bare `print "..."` statement. `print` is an ordinary identifier in
 * Onion (not a reserved word) and there is no statement form that takes an argument
 * without parentheses, so the parser trips right at the following token, expecting a
 * statement separator (`;` or a newline) -- the generic dump never mentions `println`.
 */
class PrintStatementHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("Python 2-style print statement") {
    it("hints at `println` for a bare `print \"...\"` statement") {
      val msgs = messages("""print "hello"""").mkString("\n")
      assert(msgs.contains("println(\"hello\")"), s"expected a hint mentioning `println(\"hello\")`, got: $msgs")
    }

    it("hints at `println` for a bare `print x` statement over an identifier") {
      val msgs = messages(
        """
          |val x: Int = 1
          |print x
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("println(x)"), s"expected a hint mentioning `println(x)`, got: $msgs")
    }

    it("does not fire for the correct `println(...)` call") {
      val msgs = messages("""println("hello")""").mkString("\n")
      assert(msgs.isEmpty, s"expected no errors for the correct `println(...)` call, got: $msgs")
    }

    it("does not fire for a local variable merely named `print`") {
      val msgs = messages(
        """
          |val print: Int = 5
          |println(print)
          |""".stripMargin
      ).mkString("\n")
      assert(!msgs.contains("println({0})"), s"unexpected print-statement hint, got: $msgs")
    }
  }
}
