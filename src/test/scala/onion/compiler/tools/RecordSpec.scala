package onion.compiler.tools

import onion.tools.Shell

class RecordSpec extends AbstractShellSpec {
  describe("Record type") {
    it("creates record with components and getters") {
      val result = shell.run(
        """
          |record Person(name: String, age: Int);
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p = new Person("Alice", 30);
          |    return p.name() + ":" + p.age();
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("Alice:30") == result)
    }

    it("supports nested records") {
      val result = shell.run(
        """
          |record Point(x: Int, y: Int);
          |record Line(start: Point, end: Point);
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val line = new Line(new Point(0, 0), new Point(10, 20));
          |    return line.start().x() + "," + line.start().y() + "-" + line.end().x() + "," + line.end().y();
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("0,0-10,20") == result)
    }
  }
}
