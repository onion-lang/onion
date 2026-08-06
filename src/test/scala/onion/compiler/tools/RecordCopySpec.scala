package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * Tests for issue #130: record copy with named arguments (partial copy),
 * zero-arg clone, and the original positional form.
 */
class RecordCopySpec extends AbstractShellSpec {
  private def errors(src: String): Seq[(Option[String], String)] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(e => (e.errorCode, e.message))
      case _ => Seq.empty
    }
  }

  describe("record copy") {
    it("supports partial copy with named arguments") {
      val result = shell.run(
        """
          |record Point(x: Int, y: Int)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p = new Point(1, 2)
          |    val q = p.copy(y = 9)
          |    return q.x() + "," + q.y()
          |  }
          |}
          |""".stripMargin,
        "PartialCopy.on",
        Array()
      )
      assert(Shell.Success("1,9") == result)
    }

    it("supports zero-arg clone and full named copy") {
      val result = shell.run(
        """
          |record Point(x: Int, y: Int)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p = new Point(1, 2)
          |    return p.copy().y() + "," + p.copy(x = 10, y = 20).x() + "," + p.copy(5, 6).x()
          |  }
          |}
          |""".stripMargin,
        "CloneAndFullCopy.on",
        Array()
      )
      assert(Shell.Success("2,10,5") == result)
    }

    it("reports E0005 for an unknown named component instead of crashing") {
      val results = errors(
        """
          |record Point(x: Int, y: Int)
          |class Test {
          |public:
          |  static def main(args: String[]): void {
          |    val p = new Point(1, 2)
          |    val q = p.copy(z = 9)
          |  }
          |}
          |""".stripMargin
      )
      val codes = results.map(_._1)
      assert(codes.contains(Some("E0005")))
    }

    it("falls back to the regular call path when positional and named args are mixed") {
      val result = shell.run(
        """
          |record Point(x: Int, y: Int)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p = new Point(1, 2)
          |    val q = p.copy(5, y = 9)
          |    return q.x() + "," + q.y()
          |  }
          |}
          |""".stripMargin,
        "MixedArgsCopy.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }
  }
}
