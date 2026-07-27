package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * E0011 (duplicated global variable definition). See issue #445: a *bare* top-level
 * `var`/`val` (no modifier) parses as a local variable of the synthesized script body
 * (`top_level()`'s `LOOKAHEAD(2) top=block_element()` alternative in
 * grammar/JJOnionParser.jj wins over `var_decl()` for that input), so it never reaches
 * `TypingDuplicationPass` and two bare `var x` lines are ordinary local redeclaration,
 * not a duplicate-global error. `var_decl()` — and this E0011 check — is only reachable
 * when a modifier keyword (`static`, `final`, `internal`, ...) precedes `var`/`val` at
 * top level.
 */
class DuplicateGlobalVariableSpec extends AbstractShellSpec {
  private def errors(src: String): Seq[(Option[String], String)] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(e => (e.errorCode, e.message))
      case _ => Seq.empty
    }
  }

  describe("duplicate top-level global variable definitions") {
    it("reports E0011 for two modifier-qualified globals with the same name") {
      val results = errors(
        """
          |static var x: Int = 1
          |static var x: Int = 2
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "ok" }
          |}
          |""".stripMargin
      )
      assert(results.map(_._1).contains(Some("E0011")))
    }

    it("does not report E0011 for two bare top-level vars (they are script-local, not global)") {
      val results = errors(
        """
          |var x: Int = 1
          |var x: Int = 2
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "ok" }
          |}
          |""".stripMargin
      )
      assert(!results.map(_._1).contains(Some("E0011")))
    }
  }
}
