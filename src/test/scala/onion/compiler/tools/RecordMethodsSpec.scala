package onion.compiler.tools

import onion.tools.Shell

/**
 * Record bodies: an access section after a record header declares instance
 * and static methods on the record class, exactly like enum bodies. Section
 * methods can read the synthesized accessors and call each other; operator
 * methods back the `+ - * / %` operators.
 */
class RecordMethodsSpec extends AbstractShellSpec {

  describe("Record methods") {
    it("declares an instance method that reads the accessors, a static factory, and an operator method used via +") {
      val result = shell.run(
        """
          |record Fraction(num: Int, den: Int) {
          |public:
          |  def plus(o: Fraction): Fraction = Fraction::of(num*o.den() + o.num()*den, den*o.den())
          |  def show(): String = num() + "/" + den()
          |  static def of(n: Int, d: Int): Fraction {
          |    var a: Int = n
          |    if a < 0 { a = -a }
          |    val g: Int = gcd(a, d)
          |    return new Fraction(n/g, d/g)
          |  }
          |private:
          |  static def gcd(a: Int, b: Int): Int {
          |    if b == 0 { return a }
          |    return gcd(b, a % b)
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val third: Fraction = new Fraction(1, 3)
          |    val sum: Fraction = third + third + third
          |    return sum.show()
          |  }
          |}
          |""".stripMargin,
        "RecordFractionMethods.on",
        Array()
      )
      assert(Shell.Success("1/1") == result)
    }

    it("returns a computed value from an instance method using the accessors") {
      val result = shell.run(
        """
          |record Rect(w: Int, h: Int) {
          |public:
          |  def area(): Int = w() * h()
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return "" + new Rect(4, 5).area()
          |  }
          |}
          |""".stripMargin,
        "RecordComputedValue.on",
        Array()
      )
      assert(Shell.Success("20") == result)
    }

    it("keeps a record with no body working (accessors, construction)") {
      val result = shell.run(
        """
          |record Point(x: Int, y: Int)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p: Point = new Point(2, 3)
          |    return p.x() + "," + p.y()
          |  }
          |}
          |""".stripMargin,
        "RecordNoBody.on",
        Array()
      )
      assert(Shell.Success("2,3") == result)
    }

    it("lets a body method implement a declared interface and call copy") {
      val result = shell.run(
        """
          |interface Named { def label(): String }
          |record Person(name: String, age: Int) <: Named {
          |public:
          |  def label(): String = name() + "(" + age() + ")"
          |  def older(): Person = self.copy(age = age() + 1)
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val alice: Person = new Person("Alice", 30)
          |    return alice.label() + "|" + alice.older().label()
          |  }
          |}
          |""".stripMargin,
        "RecordInterfaceBody.on",
        Array()
      )
      assert(Shell.Success("Alice(30)|Alice(31)") == result)
    }
  }
}
