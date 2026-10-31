package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/** A `val`/`var` inside a `do[M]` block (#2465): the hint must explain the block's clauses, not suggest backticks. */
class DoBlockLetHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("val inside a do block") {
    it("hints at binding with <- instead of backtick-escaping, on one line") {
      val msgs = messages("val a = Option::some(1)\nval s = do[Option] { x <- a; val y = x + 1; yield y * 10 }\n").mkString("\n")
      assert(msgs.contains("<-"), msgs)
    }

    it("hints across lines") {
      val msgs = messages("val a = Option::some(1)\nval s = do[Option] {\n  x <- a\n  var y = x + 1\n  yield y\n}\n").mkString("\n")
      assert(msgs.contains("<-"), msgs)
    }

    it("keeps the reserved-word hint outside a do block, even after one") {
      val msgs = messages("val a = do[Option] { x <- Option::some(1); yield x }\nval type = 5\n").mkString("\n")
      assert(msgs.contains("`type`") && !msgs.contains("<-"), msgs)
    }
  }
}
