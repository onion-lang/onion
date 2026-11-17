package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader
import onion.tools.Shell

/** A bare `void` statement in a `do[M]` block (#2715): the error must talk about the do block,
 *  not about a `bind` call the user never wrote. Asserts the code and locale-free fragments. */
class DoBlockVoidStatementSpec extends AbstractShellSpec {
  private def errors(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("a void statement in a do block") {
    it("is reported without naming bind, and points at yield") {
      val msg = errors("val r: Option[Int] = do[Option] {\n  x <- Option::some(1)\n  println(\"hi\")\n  yield x\n}\n").mkString("\n")
      assert(msg.contains("yield {"), msg)
      assert(!msg.contains("bind"), msg)
    }

    it("leaves a user-written bind on a void value with the plain message") {
      val msg = errors("def g(): void { }\nval x = g().bind((v) -> v)\n").mkString("\n")
      assert(msg.contains("bind") && !msg.contains("yield {"), msg)
    }

    it("still runs a side effect inside the yield block") {
      val result = shell.run(
        """class T {
          |public:
          |  static def main(args: String[]): Int {
          |    val r: Option[Int] = do[Option] {
          |      x <- Option::some(1)
          |      yield { IO::println("hi"); x + 1 }
          |    }
          |    return r.get()
          |  }
          |}
          |""".stripMargin,
        "None", Array())
      assert(Shell.Success(2) == result)
    }
  }
}
