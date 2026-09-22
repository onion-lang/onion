package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * `obj?[index] = value` (or `+=`) is rejected as a non-lvalue -- safe
 * indexing short-circuits to null at runtime, which has no sensible meaning
 * as an assignment target. Like the analogous `obj?.field = value` case
 * (`SafeNavigationAssignmentTargetSpec`), this previously fell into the
 * generic "an lvalue is required" (E0028) diagnostic with no indication that
 * the read form (`obj?[index]`) is fine and only the write form is rejected.
 * Now the safe-indexing case gets its own hint pointing at a null check or
 * dropping the `?`.
 */
class SafeIndexingAssignmentTargetSpec extends AbstractShellSpec {
  private def errors(src: String): Seq[(Option[String], String)] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(e => (e.errorCode, e.message))
      case _ => Seq.empty
    }
  }

  describe("Assigning through safe indexing (obj?[index] = value)") {
    it("reports E0028 with a safe-indexing-specific hint for a plain assignment") {
      val result = errors(
        """
          |def main: void {
          |  val arr: Int[]? = new Int[3]
          |  arr?[0] = 5
          |}
        """.stripMargin)
      assert(result.exists { case (code, msg) => code.contains("E0028") && msg.contains("?[") })
    }

    it("reports E0028 with the same hint for a compound assignment") {
      val result = errors(
        """
          |def main: void {
          |  val arr: Int[]? = new Int[3]
          |  arr?[0] += 5
          |}
        """.stripMargin)
      assert(result.exists { case (code, msg) => code.contains("E0028") && msg.contains("?[") })
    }

    it("still reports plain E0028 (no safe-nav hint) for an unrelated non-lvalue") {
      val result = errors(
        """
          |def main: void {
          |  null = 5
          |}
        """.stripMargin)
      assert(result.exists { case (code, msg) => code.contains("E0028") && !msg.contains("?[") && !msg.contains("?.") })
    }

    it("leaves the read form (obj?[index]) unaffected") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val items: Int[] = new Int[3]
          |    items[0] = 42
          |    val arr: Int[]? = items
          |    return arr?[0] ?: -1
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(onion.tools.Shell.Success(42) == result)
    }
  }
}
