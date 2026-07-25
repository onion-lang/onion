package onion.compiler.verification

import onion.compiler.{CompilationOutcome, CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * A `law` whose parameter type has no generator used to be skipped by `LawCheckPhase`
 * with a bare `return` and no diagnostic, which made it indistinguishable from a law
 * that passed. Onion's headline claim is that properties like `parse ∘ format == id`
 * are machine-checked at build time, so a check that can silently not run produces
 * unearned confidence — worse than having no check.
 *
 * Every law now either runs or reports E0074.
 */
class LawGeneratorCoverageSpec extends AnyFunSpec {

  private def errorCodes(src: String): Seq[String] = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10)
    new OnionCompiler(config)
      .compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _                                  => Seq.empty
    }
  }

  private val main =
    """|class Test {
       |public:
       |  static def main(args: String[]): String { return "ok" }
       |}
       |""".stripMargin

  describe("a law whose parameter cannot be generated") {
    it("reports E0074 for an array parameter instead of skipping silently") {
      val codes = errorCodes(
        s"""|record Dummy(v: Int)
            |  law overArray(xs: Int[]) { xs != null }
            |$main""".stripMargin
      )
      assert(codes.contains("E0074"))
    }

    it("reports E0074 for a class with more than one constructor") {
      // ArgGenerator.tryFlatRecord bails out unless there is exactly one constructor,
      // so a perfectly ordinary user class silently disabled its own law.
      val codes = errorCodes(
        s"""|class Multi {
            |public:
            |  def this { }
            |  def this(x: Int) { }
            |}
            |record Dummy(v: Int)
            |  law overMulti(m: Multi) { m != null }
            |$main""".stripMargin
      )
      assert(codes.contains("E0074"))
    }

    it("reports E0074 for an interface parameter") {
      val codes = errorCodes(
        s"""|interface Shape {
            |  def area(): Int
            |}
            |record Dummy(v: Int)
            |  law overIface(s: Shape) { s != null }
            |$main""".stripMargin
      )
      assert(codes.contains("E0074"))
    }
  }

  describe("laws that can be generated are unaffected") {
    it("does not report E0074 for a scalar parameter") {
      val codes = errorCodes(
        s"""|record Dummy(v: Int)
            |  law reflexive(n: Int) { n == n }
            |$main""".stripMargin
      )
      assert(!codes.contains("E0074"), s"unexpected E0074: $codes")
      assert(codes.isEmpty, s"expected a clean compile, got: $codes")
    }

    it("does not report E0074 for a flat record parameter") {
      val codes = errorCodes(
        s"""|record Pt(x: Int, y: Int)
            |  law reflexive(p: Pt) { p == p }
            |$main""".stripMargin
      )
      assert(!codes.contains("E0074"), s"unexpected E0074: $codes")
      assert(codes.isEmpty, s"expected a clean compile, got: $codes")
    }

    it("does not report E0074 for a zero-parameter example") {
      val codes = errorCodes(
        s"""|record R(x: Int)
            |  example { new R(1).x() == 1 }
            |$main""".stripMargin
      )
      assert(!codes.contains("E0074"), s"unexpected E0074: $codes")
      assert(codes.isEmpty, s"expected a clean compile, got: $codes")
    }
  }
}
