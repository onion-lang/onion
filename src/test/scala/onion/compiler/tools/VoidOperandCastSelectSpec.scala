package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/** A void call as the operand of `as` or the subject of `select` is E0027, not an internal error (#2891). */
class VoidOperandCastSelectSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  describe("a void call used as a value") {
    it("`u() as Object` reports E0027") {
      assert(errorCodes("def u(): void { }\nval x: Object = u() as Object\nprintln(x)\n") == Seq("E0027"))
    }
    it("`select u() { ... }` reports E0027") {
      assert(errorCodes("def u(): void { }\nselect u() { case x is String: println(\"s\")\nelse: println(\"e\") }\n") == Seq("E0027"))
    }
  }
}
