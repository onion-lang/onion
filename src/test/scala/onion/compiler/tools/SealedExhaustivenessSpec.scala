package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * A `select` over a sealed type must be exhaustive. Subtypes declared with an
 * ordinary `class` (not only `record`) are registered, so a missing case is a
 * compile error (E0042) rather than a silent `null` at runtime.
 */
class SealedExhaustivenessSpec extends AbstractShellSpec {
  private def errorCodes(program: String): Seq[Option[String]] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val inputs = Seq(new StreamInputSource(() => new StringReader(program), "SealedExhaustiveness.on"))
    new OnionCompiler(config).compile(inputs) match {
      case CompilationOutcome.Failure(errs) => errs.map(_.errorCode)
      case _ => Seq.empty
    }
  }

  describe("sealed exhaustiveness") {
    it("rejects a non-exhaustive select over a sealed class with class subtypes") {
      val codes = errorCodes(
        """
          |sealed class Expr {}
          |class Num extends Expr { public: def this { } }
          |class Add extends Expr { public: def this { } }
          |def kind(e: Expr): String { return select e { case n is Num: "num" } }
          |""".stripMargin)
      assert(codes.contains(Some("E0042")), s"expected E0042, got: $codes")
    }
    it("accepts an exhaustive select over a sealed class") {
      val r = shell.run(
        """
          |sealed class Expr {}
          |class Num extends Expr { public: def this { } }
          |class Add extends Expr { public: def this { } }
          |def kind(e: Expr): String {
          |  return select e {
          |    case n is Num: "num"
          |    case a is Add: "add"
          |  }
          |}
          |def main(args: String[]): String { return kind(new Add()) }
          |""".stripMargin, "None", Array())
      assert(Shell.Success("add") == r)
    }
    it("rejects a non-exhaustive select over a sealed interface with class subtypes") {
      val codes = errorCodes(
        """
          |sealed interface Shape {}
          |class Circle conforms Shape { public: def this { } }
          |class Square conforms Shape { public: def this { } }
          |def name(s: Shape): String { return select s { case c is Circle: "circle" } }
          |""".stripMargin)
      assert(codes.contains(Some("E0042")), s"expected E0042, got: $codes")
    }
  }
}
