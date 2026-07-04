package onion.compiler.tools

import onion.tools.Shell

/**
 * Operator overloading (+,-,*,/,%) must resolve the convention method
 * (plus/minus/times/div/rem) when it is defined via an `extension` block, not
 * only when it is a class member. Since a record has no body block, an
 * extension is the only way to overload its operators (issue #264). Previously
 * the operator path missed extensions and `a + b` silently fell back to String
 * concatenation.
 */
class OperatorExtensionMethodSpec extends AbstractShellSpec {

  describe("Operator overloading via extension methods") {
    it("resolves + to an extension plus() on a record (issue #264)") {
      val result = shell.run(
        """
          |record Vec(x: Int, y: Int)
          |extension Vec {
          |  def plus(o: Vec): Vec { return new Vec(self.x() + o.x(), self.y() + o.y()) }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a = new Vec(1, 2)
          |    val b = new Vec(3, 4)
          |    val d = a + b
          |    return d.x() + "," + d.y()
          |  }
          |}
          |""".stripMargin,
        "OpExtPlus.on",
        Array()
      )
      assert(Shell.Success("4,6") == result)
    }

    it("resolves -, * to extension methods too") {
      val result = shell.run(
        """
          |record Vec(x: Int, y: Int)
          |extension Vec {
          |  def minus(o: Vec): Vec { return new Vec(self.x() - o.x(), self.y() - o.y()) }
          |  def times(k: Int): Vec { return new Vec(self.x() * k, self.y() * k) }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a = new Vec(5, 7)
          |    val b = new Vec(1, 2)
          |    val c = a - b
          |    val e = a * 3
          |    return c.x() + "," + c.y() + ";" + e.x() + "," + e.y()
          |  }
          |}
          |""".stripMargin,
        "OpExtMinusTimes.on",
        Array()
      )
      assert(Shell.Success("4,5;15,21") == result)
    }

    it("still uses String concatenation for + when the record has no plus and a String is involved") {
      val result = shell.run(
        """
          |record Vec(x: Int, y: Int)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a = new Vec(1, 2)
          |    return "v=" + a.x()
          |  }
          |}
          |""".stripMargin,
        "OpExtConcat.on",
        Array()
      )
      assert(Shell.Success("v=1") == result)
    }

    it("prefers a class member plus over falling back (extension does not shadow members)") {
      val result = shell.run(
        """
          |class Vec {
          |  val x: Int
          |public:
          |  def this(x: Int) { this.x = x }
          |  def plus(o: Vec): Vec { return new Vec(this.x + o.x) }
          |  def show(): String { return "v" + this.x }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a = new Vec(2)
          |    val b = new Vec(3)
          |    return (a + b).show()
          |  }
          |}
          |""".stripMargin,
        "OpExtMember.on",
        Array()
      )
      assert(Shell.Success("v5") == result)
    }
  }
}
