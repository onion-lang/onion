package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/** `u() is T` on a void call is a compile error, not a VerifyError at class load (#2745). */
class IsInstanceVoidOperandSpec extends AnyFunSpec {

  private def errorCodes(source: String): Seq[String] = {
    val compiler = new OnionCompiler(CompilerConfig(Seq("."), null, "UTF-8", "", 10))
    val result = compiler.compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "Test.on")))
    result.diagnostics.errors.map(_.toString)
  }

  describe("is on a void expression") {
    it("is rejected with E0027") {
      val errs = errorCodes("def u(): void { }\nprintln(u() is Object)\n")
      assert(errs.exists(_.contains("E0027")), errs.mkString("; "))
    }
    it("still accepts a primitive and a reference operand") {
      assert(errorCodes("val n: Int = 3\nprintln(n is Object)\nval s: String = \"a\"\nprintln(s is Object)\n").isEmpty)
    }
  }
}
