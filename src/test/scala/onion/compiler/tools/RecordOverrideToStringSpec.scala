package onion.compiler.tools

import onion.tools.Shell

class RecordOverrideToStringSpec extends AbstractShellSpec {
  describe("Record with user-defined toString/equals/hashCode in body") {
    it("can override toString without duplicate-method crash") {
      val result = shell.run(
        """
          |record Point(x: Int, y: Int) {
          |public:
          |  override def toString(): String = "Point(" + x() + ", " + y() + ")"
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    return new Point(3, 4).toString()
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("Point(3, 4)") == result)
    }

    it("can override equals without duplicate-method crash") {
      val result = shell.run(
        """
          |record Box(value: Int) {
          |public:
          |  override def equals(o: Object): Boolean {
          |    if o == null { return false }
          |    if !(o is Box) { return false }
          |    return (o as Box).value() == value()
          |  }
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    val b1: Box = new Box(42)
          |    val b2: Box = new Box(42)
          |    val b3: Box = new Box(7)
          |    return "" + b1.equals(b2) + "," + b1.equals(b3)
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("true,false") == result)
    }

    it("synthetic toString is still generated when body has no override") {
      val result = shell.run(
        """
          |record Tag(name: String) { }
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    val t: String = new Tag("hello").toString()
          |    return if t != null { "ok" } else { "null" }
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("can define both toString and a custom method in the body") {
      val result = shell.run(
        """
          |record Vec2(x: Double, y: Double) {
          |public:
          |  override def toString(): String = "(" + x() + "," + y() + ")"
          |  def length(): Double = Math::sqrt(x() * x() + y() * y())
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    val v: Vec2 = new Vec2(3.0d, 4.0d)
          |    return v.toString() + " len=" + v.length()
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("(3.0,4.0) len=5.0") == result)
    }

    it("can override hashCode without duplicate-method crash") {
      val result = shell.run(
        """
          |record Named(name: String) {
          |public:
          |  override def hashCode(): Int = name().hashCode() * 31
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    val n: Named = new Named("test")
          |    return if n.hashCode() != 0 { "ok" } else { "zero" }
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }
  }
}
