package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A type error inside a lambda must be reported when the lambda goes to an unqualified call
 * that is itself the argument of another unqualified call (`p(call { ... })`). The outer call
 * typed that argument with errors muted and kept the result, so the program compiled and then
 * failed with a VerifyError, or ran code that was never type-checked (#3174).
 */
class NestedUnqualifiedLambdaErrorSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  private val defs =
    """def call(f: (Int) -> String): String = f(1)
      |def p(s: String): void = println(s)
      |""".stripMargin

  describe("a type error in a lambda nested in an unqualified call argument (#3174)") {
    it("is E0000 for p(call { ... })") {
      assert(errorCodes(defs + "p(call { x -> val n: Int = \"s\"; \"got \" + (n + 1) })\n") == Seq("E0000"))
    }
    it("is E0000 when the lambda's value does not use the bad local") {
      assert(errorCodes(defs + "p(call { x -> val n: Int = \"s\"; \"y\" })\n") == Seq("E0000"))
    }
    it("is E0000 for a bare println(call { ... })") {
      assert(errorCodes(defs + "println(call { x -> val n: Int = \"s\"; \"y\" })\n") == Seq("E0000"))
    }
    it("is E0000 for an arrow lambda, call((x) -> { ... })") {
      assert(errorCodes(defs + "p(call((x) -> { val n: Int = \"s\"; \"y\" }))\n") == Seq("E0000"))
    }
    it("is E0000 inside a class's static main") {
      val src =
        """class C {
          |public:
          |  static def call(f: (Int) -> String): String = f(1)
          |  static def p(s: String): void = println(s)
          |  static def main(args: String[]): void {
          |    p(call { x -> val n: Int = "s"; "y" })
          |  }
          |}
          |""".stripMargin
      assert(errorCodes(src) == Seq("E0000"))
    }
    it("still reports the error once where it was already reported, val s = call { ... }") {
      assert(errorCodes(defs + "val s = call { x -> val n: Int = \"s\"; \"y\" }\n") == Seq("E0000"))
    }
    it("leaves a correct nested call running") {
      val src =
        """class C {
          |public:
          |  static def call(f: (Int) -> String): String = f(1)
          |  static def id(s: String): String = s
          |  static def main(args: String[]): String = id(call { x -> "got " + (x + 1) })
          |}
          |""".stripMargin
      assert(shell.run(src, "NestedUnqualifiedLambda.on", Array()) == onion.tools.Shell.Success("got 2"))
    }
  }
}
