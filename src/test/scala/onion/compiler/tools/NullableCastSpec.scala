package onion.compiler.tools

import onion.compiler.{CompilationOutcome, CompilerConfig, OnionCompiler, StreamInputSource}
import onion.tools.Shell

import java.io.StringReader

/** A cast to or from a nullable type is checked like the non-null form, and
 *  `null` still casts to any nullable type, primitive ones included (#2388). */
class NullableCastSpec extends AbstractShellSpec {
  private def compileErrors(src: String): String = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(e => s"${e.code} ${e.message}").mkString("\n")
      case _ => ""
    }
  }

  describe("a cast involving a nullable type") {
    it("accepts null cast to a nullable primitive type") {
      val r = shell.run(
        """def main(args: String[]): Int {
          |  val a = (null as Int?)
          |  val b = (null as Long?)
          |  return if a == null && b == null { 1 } else { 0 }
          |}
          |""".stripMargin, "NullAsNullablePrimitive.on", Array())
      assert(Shell.Success(1) == r)
    }
    it("pins an overload with null cast to a nullable primitive type") {
      val r = shell.run(
        """def f(x: Int?): Int = 1
          |def f(x: String?): Int = 2
          |def main(args: String[]): Int { return f(null as Int?) * 10 + f(null as String?) }
          |""".stripMargin, "NullOverloadPin.on", Array())
      assert(Shell.Success(12) == r)
    }
    it("reports an invalid cast from a nullable source without the `!!` hint") {
      val msgs = compileErrors(
        """def main(args: String[]): void {
          |  val s: String? = "a"
          |  val n = (s as Integer)
          |}
          |""".stripMargin)
      assert(msgs.contains("E0000"), s"expected E0000, got: $msgs")
      assert(!msgs.contains("!!"), s"`!!` does not make this cast valid, got: $msgs")
    }
    it("still gives the `!!` hint where a nullable value meets a non-null type") {
      val msgs = compileErrors(
        """def main(args: String[]): void {
          |  val s: String? = "a"
          |  val t: String = s
          |}
          |""".stripMargin)
      assert(msgs.contains("!!"), s"expected the null-safety hint, got: $msgs")
    }
  }
}
