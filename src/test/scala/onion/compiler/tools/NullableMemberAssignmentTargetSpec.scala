package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome, CompileError}
import java.io.StringReader

/**
 * Assigning through a genuinely nullable (non-safe-nav) receiver (`b.field =
 * value` where `b: Box?`) used to report the generic `E0041`
 * (INVALID_METHOD_CALL_TARGET, "type Box? is not a valid method call
 * target"), with no indication that `?.`, `?:`, `!!`, or a null check would
 * fix it -- even though the equivalent read (`b.field`) already reports the
 * specific, actionable `E0070` (NULLABLE_MEMBER_ACCESS) diagnostic, and the
 * equivalent plain method call on the same nullable receiver (`b.method()`)
 * already reports `E0070` too. `AssignmentTyping.processMemberAssign` never
 * special-cased a `NullableType` assignment target before falling into the
 * generic `INVALID_METHOD_CALL_TARGET` catch-all, unlike the read path
 * (`MemberSelectionResolutionSupport.normalizeTarget`) and the method-call
 * path (`MethodTargetTypingSupport.normalizeMethodCallTarget`), which both
 * already special-case it. This is distinct from the `obj?.field = value`
 * case (`SafeNavigationAssignmentTargetSpec`): here there is no `?.` at all --
 * the receiver itself is simply typed as nullable.
 */
class NullableMemberAssignmentTargetSpec extends AbstractShellSpec {
  private def errors(src: String): Seq[CompileError] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs
      case _ => Seq.empty
    }
  }

  private val boxClass =
    """
      |class Box {
      |public:
      |  var count: Int
      |  def this { self.count = 0 }
      |}
      |""".stripMargin

  describe("Assigning through a nullable (non-safe-nav) receiver (b.field = value)") {
    it("reports E0070, not E0041, for a plain field assignment") {
      val results = errors(
        boxClass +
        """
          |def main: void {
          |  val b: Box? = new Box()
          |  b.count = 5
          |}
        """.stripMargin)
      assert(!results.map(_.errorCode).contains(Some("E0041")))
      assert(results.map(_.errorCode).contains(Some("E0070")))
    }

    it("reports E0070, not E0041, for a compound assignment") {
      val results = errors(
        boxClass +
        """
          |def main: void {
          |  val b: Box? = new Box()
          |  b.count += 5
          |}
        """.stripMargin)
      assert(!results.map(_.errorCode).contains(Some("E0041")))
      assert(results.map(_.errorCode).contains(Some("E0070")))
    }

    it("matches the error code already reported for the equivalent read (b.field)") {
      val results = errors(
        boxClass +
        """
          |def main: void {
          |  val b: Box? = new Box()
          |  IO::println(b.count)
          |}
        """.stripMargin)
      assert(results.map(_.errorCode).contains(Some("E0070")))
    }

    it("still reports E0070 (unchanged) for the equivalent plain method call on the same nullable receiver") {
      val results = errors(
        boxClass +
        """
          |def main: void {
          |  val b: Box? = new Box()
          |  b.toString()
          |}
        """.stripMargin)
      assert(results.map(_.errorCode).contains(Some("E0070")))
    }

    it("leaves a safely-navigated assignment (b?.field = value) reporting its own E0028 hint, unaffected") {
      val results = errors(
        boxClass +
        """
          |def main: void {
          |  val b: Box? = new Box()
          |  b?.count = 5
          |}
        """.stripMargin)
      assert(results.map(_.errorCode).contains(Some("E0028")))
    }
  }
}
