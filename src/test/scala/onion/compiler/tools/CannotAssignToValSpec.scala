package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * E0036 (CANNOT_ASSIGN_TO_VAL) has several distinct report sites (local val,
 * instance field, static field, pre/post-increment) but had no regression
 * coverage at all before this spec.
 */
class CannotAssignToValSpec extends AbstractShellSpec {
  private def errors(src: String): Seq[(Option[String], String)] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(e => (e.errorCode, e.message))
      case _ => Seq.empty
    }
  }

  describe("assigning to a val") {
    it("reports E0036 when reassigning a local val") {
      val results = errors(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val x: Int = 1;
          |    x = 2;
          |    return x;
          |  }
          |}
          |""".stripMargin
      )
      assert(results.map(_._1).contains(Some("E0036")))
    }

    it("reports E0036 when post-incrementing a local val") {
      val results = errors(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val x: Int = 1;
          |    x++;
          |    return x;
          |  }
          |}
          |""".stripMargin
      )
      assert(results.map(_._1).contains(Some("E0036")))
    }

    it("reports E0036 when assigning an instance val field from outside the constructor") {
      val results = errors(
        """
          |class Test {
          |  val x: Int = 1
          |public:
          |  def bump(): void {
          |    this.x = 2;
          |  }
          |  static def main(args: String[]): Int {
          |    return 0;
          |  }
          |}
          |""".stripMargin
      )
      assert(results.map(_._1).contains(Some("E0036")))
    }

    it("allows initializing an instance val field from within the constructor") {
      val results = errors(
        """
          |class Test {
          |  val x: Int
          |public:
          |  def this {
          |    this.x = 1;
          |  }
          |  static def main(args: String[]): Int {
          |    return new Test().x;
          |  }
          |}
          |""".stripMargin
      )
      assert(!results.map(_._1).contains(Some("E0036")))
    }

    it("does not report E0036 when reassigning a local var") {
      val results = errors(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    var x: Int = 1;
          |    x = 2;
          |    return x;
          |  }
          |}
          |""".stripMargin
      )
      assert(!results.map(_._1).contains(Some("E0036")))
    }
  }
}
