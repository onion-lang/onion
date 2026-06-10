package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for issue #130: record copy with named arguments (partial copy),
 * zero-arg clone, and the original positional form.
 */
class RecordCopySpec extends AbstractShellSpec {

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
  }
}
