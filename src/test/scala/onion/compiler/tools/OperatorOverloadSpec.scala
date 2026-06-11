package onion.compiler.tools

import onion.tools.Shell

/**
 * Kotlin-style operator overloading: +,-,*,/,% dispatch to the convention
 * methods plus/minus/times/div/rem on the left operand. String keeps
 * concatenation for +; numeric operands keep primitive arithmetic.
 */
class OperatorOverloadSpec extends AbstractShellSpec {

  describe("Operator overloading") {
    it("dispatches +, - and * to convention methods") {
      val result = shell.run(
        """
          |class Vec {
          |  val x: Int
          |  val y: Int
          |public:
          |  def this(x: Int, y: Int) { this.x = x; this.y = y }
          |  def plus(o: Vec): Vec { return new Vec(this.x + o.x, this.y + o.y) }
          |  def minus(o: Vec): Vec { return new Vec(this.x - o.x, this.y - o.y) }
          |  def times(k: Int): Vec { return new Vec(this.x * k, this.y * k) }
          |  def show(): String { return "(" + this.x + "," + this.y + ")" }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a = new Vec(1, 2)
          |    val b = new Vec(3, 4)
          |    return (a + b).show() + (b - a).show() + (a * 3).show()
          |  }
          |}
          |""".stripMargin,
        "OpVec.on",
        Array()
      )
      assert(Shell.Success("(4,6)(2,2)(3,6)") == result)
    }

    it("keeps string concatenation for + when a String is involved") {
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
          |    val a = new Vec(5)
          |    return "sum=" + a.show() + "," + (1 + 2)
          |  }
          |}
          |""".stripMargin,
        "OpStr.on",
        Array()
      )
      assert(Shell.Success("sum=v5,3") == result)
    }

    it("works through compound assignment") {
      val result = shell.run(
        """
          |class Money {
          |  val cents: Int
          |public:
          |  def this(c: Int) { this.cents = c }
          |  def plus(o: Money): Money { return new Money(this.cents + o.cents) }
          |  def show(): String { return "$" + this.cents }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    var m = new Money(100)
          |    m += new Money(50)
          |    return m.show()
          |  }
          |}
          |""".stripMargin,
        "OpCompound.on",
        Array()
      )
      assert(Shell.Success("$150") == result)
    }

    it("reports an error when no convention method exists") {
      val result = shell.run(
        """
          |class Plain {
          |public:
          |  def this {}
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p = new Plain()
          |    val q = p - p
          |    return "no"
          |  }
          |}
          |""".stripMargin,
        "OpMissing.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }
  }
}
