package onion.compiler.verification

import onion.compiler.{CompilationOutcome, CompileError, CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Law and example diagnostics used to arrive with `location = null` and the *class name*
 * in the `sourceFile` slot, so they rendered as `Pt:: [E0064] ...` — no file, no line, no
 * caret, and line 0 column 0 in the editor. The clause's `Location` was captured during
 * rewriting and then dropped.
 *
 * The location now survives, via the line-number table the backend already emits.
 */
class LawDiagnosticLocationSpec extends AnyFunSpec {

  private def errors(src: String): Seq[CompileError] = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10)
    new OnionCompiler(config)
      .compile(Seq(new StreamInputSource(() => new StringReader(src), "sample.on"))) match {
      case CompilationOutcome.Failure(es) => es
      case _                              => Seq.empty
    }
  }

  private val main =
    """|class Test {
       |public:
       |  static def main(args: String[]): String { return "ok" }
       |}
       |""".stripMargin

  describe("a falsified law") {
    // `law wrong` is on line 3 of this source.
    val src =
      s"""|
          |record Pt(x: Int, y: Int)
          |  law wrong(p: Pt) { p.x() == p.y() }
          |$main""".stripMargin

    it("names the source file the law was written in, not the record") {
      val e = errors(src).find(_.errorCode.contains("E0064")).get
      assert(e.sourceFile == "sample.on", s"got '${e.sourceFile}'")
    }

    it("carries the line the law is written on") {
      val e = errors(src).find(_.errorCode.contains("E0064")).get
      assert(e.location != null, "law diagnostics still have no location")
      assert(e.location.line == 3, s"expected line 3, got ${e.location.line}")
    }
  }

  describe("a failed example") {
    val src =
      s"""|record R(x: Int)
          |  example { new R(1).x() == 2 }
          |$main""".stripMargin

    it("carries a location too") {
      val e = errors(src).find(_.errorCode.contains("E0065")).get
      assert(e.sourceFile == "sample.on")
      assert(e.location != null)
      assert(e.location.line == 2, s"expected line 2, got ${e.location.line}")
    }
  }

  describe("a law that cannot be generated") {
    it("carries a location as well") {
      val src =
        s"""|record Dummy(v: Int)
            |  law overArray(xs: Int[]) { xs != null }
            |$main""".stripMargin
      val e = errors(src).find(_.errorCode.contains("E0074")).get
      assert(e.sourceFile == "sample.on")
      assert(e.location != null)
      assert(e.location.line == 2, s"expected line 2, got ${e.location.line}")
    }
  }
}
