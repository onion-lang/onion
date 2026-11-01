package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/** A `val`/`var` inside a `do[M]` block (#2465): the hint must explain the block's clauses, not
 *  suggest backticks, and any bind form it names must compile for that `M`. */
class DoBlockLetHintSpec extends AbstractShellSpec {
  private def compile(src: String): CompilationOutcome = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on")))
  }

  private def messages(src: String): Seq[String] = compile(src) match {
    case CompilationOutcome.Failure(errors) => errors.map(_.message)
    case _ => Seq.empty
  }

  private def compiles(src: String): Boolean = compile(src) match {
    case CompilationOutcome.Failure(errors) => fail(errors.map(_.message).mkString("\n"))
    case _ => true
  }

  private val prelude = "import { onion.Result }\nval r: Result[Int, String] = Result::ok(1)\nval f = Future::successful(1)\n"

  describe("val inside a do block") {
    it("hints at binding with <- instead of backtick-escaping, on one line") {
      val msgs = messages("val a = Option::some(1)\nval s = do[Option] { x <- a; val y = x + 1; yield y * 10 }\n").mkString("\n")
      assert(msgs.contains("y <- Option::some(x + 1)") && !msgs.contains("::of("), msgs)
      assert(compiles("val a = Option::some(1)\nval s = do[Option] { x <- a; y <- Option::some(x + 1); yield y * 10 }\n"))
    }

    it("hints across lines") {
      val msgs = messages("val a = Option::some(1)\nval s = do[Option] {\n  x <- a\n  var y = x + 1\n  yield y\n}\n").mkString("\n")
      assert(msgs.contains("y <- Option::some(x + 1)"), msgs)
    }

    it("names Future::successful in do[Future], and that form compiles") {
      val msgs = messages(prelude + "val s = do[Future] { x <- f; val y = x + 1; yield y * 10 }\n").mkString("\n")
      assert(msgs.contains("y <- Future::successful(x + 1)"), msgs)
      assert(compiles(prelude + "val s = do[Future] { x <- f; y <- Future::successful(x + 1); yield y * 10 }\n"))
    }

    it("names a one-element list in do[List], and that form compiles") {
      val msgs = messages("val s = do[List] { x <- [1, 2]; val y = x + 1; yield y * 10 }\n").mkString("\n")
      assert(msgs.contains("y <- [x + 1]"), msgs)
      assert(compiles("val s = do[List] { x <- [1, 2]; y <- [x + 1]; yield y * 10 }\n"))
    }

    it("gives only the yield advice in do[Result], which compiles") {
      val msgs = messages(prelude + "val s = do[Result] { x <- r; val y = x + 1; yield y * 10 }\n").mkString("\n")
      assert(msgs.contains("do[Result]") && msgs.contains("`yield`") && !msgs.contains("Result::"), msgs)
      assert(compiles(prelude + "val s = do[Result] { x <- r; yield (x + 1) * 10 }\n"))
    }

    it("does not claim plain expressions are allowed") {
      val msgs = messages("val a = Option::some(1)\nval s = do[Option] { x <- a; val y = x + 1; yield y }\n").mkString("\n")
      assert(!msgs.contains("plain expressions"), msgs)
    }

    it("keeps the reserved-word hint outside a do block, even after one") {
      val msgs = messages("val a = do[Option] { x <- Option::some(1); yield x }\nval type = 5\n").mkString("\n")
      assert(msgs.contains("`type`") && !msgs.contains("<-"), msgs)
    }
  }
}
