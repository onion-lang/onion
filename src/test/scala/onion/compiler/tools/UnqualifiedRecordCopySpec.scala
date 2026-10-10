package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * `p.copy(...)` (RecordCopySpec) already supports a zero-arg clone, a partial
 * named copy, and a full named copy. A bare, receiver-less `copy(...)` called
 * from inside the record's own method body (implicit self receiver) took a
 * different resolution path that never special-cased `copy` at all, so it
 * missed the same sugar entirely.
 */
class UnqualifiedRecordCopySpec extends AbstractShellSpec {
  private def errors(src: String): Seq[(Option[String], String)] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(e => (e.errorCode, e.message))
      case _ => Seq.empty
    }
  }

  describe("bare copy(...) inside a record's own method") {
    it("supports a zero-arg bare copy() as a full clone") {
      val result = shell.run(
        """
          |record Point(x: Int, y: Int) {
          |public:
          |  def cloned(): Point = copy()
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p = new Point(1, 2)
          |    val q = p.cloned()
          |    return q.x() + "," + q.y()
          |  }
          |}
          |""".stripMargin,
        "BareCloneCopy.on",
        Array()
      )
      assert(Shell.Success("1,2") == result)
    }

    it("supports a bare partial named copy") {
      val result = shell.run(
        """
          |record Acc(total: Int) {
          |public:
          |  def add(n: Int): Acc = copy(total = total() + n)
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    var acc = new Acc(0)
          |    acc = acc.add(3)
          |    acc = acc.add(4)
          |    return "" + acc.total()
          |  }
          |}
          |""".stripMargin,
        "BarePartialCopy.on",
        Array()
      )
      assert(Shell.Success("7") == result)
    }

    it("supports a bare full named copy") {
      val result = shell.run(
        """
          |record Point(x: Int, y: Int) {
          |public:
          |  def shifted(dx: Int, dy: Int): Point = copy(x = x() + dx, y = y() + dy)
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p = new Point(1, 2)
          |    val q = p.shifted(10, 20)
          |    return q.x() + "," + q.y()
          |  }
          |}
          |""".stripMargin,
        "BareFullCopy.on",
        Array()
      )
      assert(Shell.Success("11,22") == result)
    }

    it("reports E0005 for an unknown named component, same as the qualified call") {
      val results = errors(
        """
          |record Point(x: Int, y: Int) {
          |public:
          |  def bad(): Point = copy(z = 9)
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): void {}
          |}
          |""".stripMargin
      )
      val codes = results.map(_._1)
      assert(codes.contains(Some("E0005")))
    }
  }
}
