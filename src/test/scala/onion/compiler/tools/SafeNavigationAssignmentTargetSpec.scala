package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * `obj?.field = value` (or `+=`) is rejected as a non-lvalue -- safe navigation
 * short-circuits to null at runtime, which has no sensible meaning as an
 * assignment target. Previously this fell into the same generic "an lvalue is
 * required" (E0028) diagnostic as any other non-assignable expression (e.g.
 * `null = expr`), giving no hint that the read form (`obj?.field`) is fine and
 * only the write form is rejected. Now the safe-navigation case gets an extra
 * hint pointing at a null check or dropping the `?.`.
 */
class SafeNavigationAssignmentTargetSpec extends AbstractShellSpec {
  private def errors(src: String): Seq[(Option[String], String)] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(e => (e.errorCode, e.message))
      case _ => Seq.empty
    }
  }

  describe("Assigning through safe navigation (obj?.field = value)") {
    it("reports E0028 with a safe-navigation-specific hint for a plain assignment") {
      val result = errors(
        """
          |class Box {
          |public:
          |  var count: Int
          |  def this { self.count = 0 }
          |}
          |def main: void {
          |  val b: Box? = new Box()
          |  b?.count = 5
          |}
        """.stripMargin)
      assert(result.exists { case (code, msg) => code.contains("E0028") && msg.contains("?.") })
    }

    it("reports E0028 with the same hint for a compound assignment") {
      val result = errors(
        """
          |class Box {
          |public:
          |  var count: Int
          |  def this { self.count = 0 }
          |}
          |def main: void {
          |  val b: Box? = new Box()
          |  b?.count += 5
          |}
        """.stripMargin)
      assert(result.exists { case (code, msg) => code.contains("E0028") && msg.contains("?.") })
    }

    it("still reports plain E0028 (no safe-nav hint) for an unrelated non-lvalue") {
      val result = errors(
        """
          |def main: void {
          |  null = 5
          |}
        """.stripMargin)
      assert(result.exists { case (code, msg) => code.contains("E0028") && !msg.contains("?.") })
    }

    it("leaves the read form (obj?.field) unaffected") {
      val result = shell.run(
        """
          |class Box {
          |public:
          |  var count: Int
          |  def this { self.count = 42 }
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val b: Box? = new Box()
          |    return b?.count ?: -1
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
