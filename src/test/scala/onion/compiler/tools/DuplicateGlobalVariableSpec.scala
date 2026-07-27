package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * E0011 (duplicated global variable definition). See issue #445: a *bare* top-level
 * `var`/`val` (no modifier) parses as a local variable of the synthesized script body
 * (`top_level()`'s `LOOKAHEAD(2) top=block_element()` alternative in
 * grammar/JJOnionParser.jj wins over `var_decl()` for that input), so it never reaches
 * `TypingDuplicationPass` — that check only sees the `AST.GlobalVariableDeclaration`
 * node produced by a modifier-qualified top-level `var`/`val`.
 *
 * A bare top-level `var`/`val` is instead promoted to a `public static` field of the
 * script's synthetic class by `TypingBodyPass.processTopLevelVarDeclaration`, so it is
 * just as global as the modifier-qualified form. That method reports the duplicate
 * itself (reusing E0011) when the field already exists.
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

    it("reports E0011 for two bare top-level vars with the same name") {
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
      assert(results.map(_._1).contains(Some("E0011")))
    }

    it("reports E0011 for a bare top-level val re-declared as a var") {
      val results = errors(
        """
          |val x: Int = 1
          |var x: Int = 2
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "ok" }
          |}
          |""".stripMargin
      )
      assert(results.map(_._1).contains(Some("E0011")))
    }
  }
}
