package onion.tools.lsp

import onion.compiler.{CompilationOutcome, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * `law` and `example` clauses are executed by the compiler — real user code, invoked
 * reflectively by LawCheckPhase. The language server validates on every keystroke, so
 * running them there means the editor executes whatever the file currently says, in a
 * half-typed state, over and over.
 *
 * The editor therefore compiles with law checking off. Laws still run for `onionc`,
 * `onion`, `onion build` and `onion test`, which is where a build-time check belongs.
 */
class LspLawExecutionSpec extends AnyFunSpec {

  private def errorCodes(config: onion.compiler.CompilerConfig, src: String): Seq[String] =
    new OnionCompiler(config)
      .compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _                                  => Seq.empty
    }

  private val main =
    """|class Test {
       |public:
       |  static def main(args: String[]): String { return "ok" }
       |}
       |""".stripMargin

  private val falsifiedLaw =
    s"""|record Pt(x: Int, y: Int)
        |  law wrong(p: Pt) { p.x() == p.y() }
        |$main""".stripMargin

  describe("the language server's validation config") {
    it("does not execute laws") {
      assert(!OnionTextDocumentService.validationConfig.checkLaws)
    }

    it("reports no law violation for a law that a build would reject") {
      val codes = errorCodes(OnionTextDocumentService.validationConfig, falsifiedLaw)
      assert(!codes.contains("E0064"), s"the editor executed a law: $codes")
    }

    it("still reports ordinary compile errors") {
      val codes = errorCodes(
        OnionTextDocumentService.validationConfig,
        s"""|record R(x: Int)
            |  law bad(xs: Int[]) { xs != null }
            |$main""".stripMargin
      )
      // E0074 comes from LawCheckPhase too, so switching the phase off must not be
      // mistaken for "the editor reports nothing about laws" -- it reports nothing
      // *executed*. This case pins that the phase really is off.
      assert(!codes.contains("E0074"))

      val typeError = errorCodes(
        OnionTextDocumentService.validationConfig,
        s"""|class Broken {
            |public:
            |  static def f(): Int { return "not an int" }
            |}
            |$main""".stripMargin
      )
      assert(typeError.nonEmpty, "the editor stopped reporting type errors")
    }
  }

  describe("a build still executes laws") {
    it("reports the law violation the editor stayed quiet about") {
      val buildConfig = onion.compiler.CompilerConfig(Seq("."), null, "UTF-8", "", 10)
      assert(buildConfig.checkLaws)
      assert(errorCodes(buildConfig, falsifiedLaw).contains("E0064"))
    }
  }
}
