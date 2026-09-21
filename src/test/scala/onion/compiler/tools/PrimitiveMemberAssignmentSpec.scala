package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome, CompileError}
import java.io.StringReader

/**
 * Assigning to a nonexistent member on a primitive-typed target (`n.bogus = 5`
 * where `n: Int`) used to report the read path's error code, `E0000`
 * (INCOMPATIBLE_TYPE, "type Object is expected"), with the caret on the
 * receiver -- because `AssignmentTyping.processMemberAssign` bailed out on any
 * `BasicType` target instead of boxing it first, unlike the read path
 * (`MemberSelectionResolutionSupport.normalizeTarget`), which boxes a
 * primitive target before field/getter lookup. The equivalent read
 * (`n.bogus`) and the equivalent assignment on a reference-typed target
 * (`s.bogus = 5` where `s: String`) both already reported the correct
 * `E0004` (FIELD_NOT_FOUND).
 */
class PrimitiveMemberAssignmentSpec extends AbstractShellSpec {
  private def errors(src: String): Seq[CompileError] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs
      case _ => Seq.empty
    }
  }

  describe("assigning to an undeclared member on a primitive-typed target") {
    it("reports E0004, not E0000, for a plain field assignment") {
      val results = errors(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val n: Int = 3;
          |    n.bogus = 5;
          |    return 0;
          |  }
          |}
          |""".stripMargin
      )
      assert(!results.map(_.errorCode).contains(Some("E0000")))
      assert(results.map(_.errorCode).contains(Some("E0004")))
    }

    it("positions the error at the member name, not the receiver") {
      val results = errors(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val n: Int = 3;
          |    n.bogus = 5;
          |    return 0;
          |  }
          |}
          |""".stripMargin
      )
      val fieldNotFound = results.find(_.errorCode == Some("E0004"))
      assert(fieldNotFound.isDefined)
      // "    n.bogus = 5;" -- column 1 is the receiver `n`, `bogus` starts at column 7.
      assert(fieldNotFound.get.location.column > 5)
    }

    it("reports E0004, not E0000, for a compound assignment") {
      val results = errors(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val n: Int = 3;
          |    n.bogus += 1;
          |    return 0;
          |  }
          |}
          |""".stripMargin
      )
      assert(!results.map(_.errorCode).contains(Some("E0000")))
      assert(results.map(_.errorCode).contains(Some("E0004")))
    }

    it("still reports E0004 for the equivalent reference-typed target (unchanged behavior)") {
      val results = errors(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val s: String = "hello";
          |    s.bogus = 5;
          |    return 0;
          |  }
          |}
          |""".stripMargin
      )
      assert(results.map(_.errorCode).contains(Some("E0004")))
    }
  }
}
