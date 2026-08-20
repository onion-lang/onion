package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * Onion declares functions and methods with `def`, not the `fun` (Kotlin),
 * `func` (Swift/Go), or `fn` (Rust) keyword. None of those are keywords in
 * Onion, so the parser trips either on the declaration name that follows (at
 * statement position) or on the keyword itself (inside a class/interface body),
 * never mentioning `def`. The hint keys off the source line so both spellings
 * are covered.
 */
class FunDeclarationHintSpec extends AbstractShellSpec {
  private def messages(src: String): String = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
  }

  describe("fun/func/fn declaration") {
    it("hints at def for a top-level Kotlin-style fun declaration") {
      val msgs = messages("fun greet(): void { IO::println(\"hi\") }\n")
      assert(msgs.contains("def"), s"expected a hint mentioning def, got: $msgs")
      assert(msgs.contains("fun"), s"expected the hint to name fun, got: $msgs")
    }

    it("hints at def for a Swift/Go-style func member") {
      val msgs = messages(
        """
          |interface I {
          |  func foo(): Int
          |}
          |""".stripMargin
      )
      assert(msgs.contains("def"), s"expected a hint mentioning def, got: $msgs")
    }

    it("hints at def for a Rust-style fn declaration") {
      val msgs = messages("fn main(): void { }\n")
      assert(msgs.contains("def"), s"expected a hint mentioning def, got: $msgs")
    }

    it("does not fire for an unrelated bare-identifier-statement typo") {
      val msgs = messages("foo bar\n")
      assert(!msgs.contains("declared with"), s"hint should not fire without a leading fun/func/fn, got: $msgs")
    }

    it("does not fire for a call to a method literally named fun") {
      val msgs = messages("val x = fun(1, 2)\n")
      assert(!msgs.contains("declared with"), s"hint should not fire for a fun(...) call, got: $msgs")
    }
  }
}
