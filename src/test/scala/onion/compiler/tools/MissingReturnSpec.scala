package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * A value-returning method whose block body can complete without returning must
 * be rejected (E0067). Onion block bodies use an explicit `return`; the `= expr`
 * form is the expression body. Previously such a method silently returned the
 * JVM default (0 / null) with no diagnostic.
 */
class MissingReturnSpec extends AbstractShellSpec {
  private def errorCodes(program: String): Seq[Option[String]] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val inputs = Seq(new StreamInputSource(() => new StringReader(program), "MissingReturn.on"))
    new OnionCompiler(config).compile(inputs) match {
      case CompilationOutcome.Failure(errs) => errs.map(_.errorCode)
      case _ => Seq.empty
    }
  }

  describe("missing return") {
    it("rejects a value-returning body with no return") {
      val codes = errorCodes(
        """
          |class Foo {
          |public:
          |  def bar(): Int { IO::println("x") }
          |}
          |""".stripMargin)
      assert(codes.contains(Some("E0067")), s"expected E0067, got: $codes")
    }

    it("rejects a body whose tail expression is not returned") {
      val codes = errorCodes(
        """
          |class Foo {
          |public:
          |  def compute(): Int { 5 + 10 }
          |}
          |""".stripMargin)
      assert(codes.contains(Some("E0067")), s"expected E0067, got: $codes")
    }

    it("rejects a body that returns on only some paths") {
      val codes = errorCodes(
        """
          |class Foo {
          |public:
          |  def bar(n: Int): Int { if n > 0 { return 1 } }
          |}
          |""".stripMargin)
      assert(codes.contains(Some("E0067")), s"expected E0067, got: $codes")
    }

    it("accepts explicit return, = expr, if/else, and while-true bodies") {
      val r = shell.run(
        """
          |class Foo {
          |public:
          |  def a(): Int { return 5 }
          |  def b(): Int = 5
          |  def c(): Int = { 5 + 10 }
          |  def d(n: Int): Int { if n > 0 { return 1 } else { return 2 } }
          |  def loop(): Int { while true { return 7 } }
          |  def v(): void { IO::println("side") }
          |  static def main(args: String[]): Int {
          |    val f = new Foo()
          |    return f.a() + f.b() + f.c() + f.d(1) + f.loop()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(33) == r)  // 5 + 5 + 15 + 1 + 7
    }
  }
}
