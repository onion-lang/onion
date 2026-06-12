package onion.compiler.tools

import onion.tools.Shell

/**
 * Implicit field access (#162): a bare name with no local binding resolves to
 * a field of the current class -- this.<name> for an instance field, the
 * static field directly for a static one -- so `this.`/`Class::` qualification
 * is optional. Local variables still shadow fields.
 */
class ImplicitFieldAccessSpec extends AbstractShellSpec {
  describe("implicit field access") {
    it("reads an instance field without this.") {
      val result = shell.run(
        """
          |class Circle {
          |  val r: Double
          |public:
          |  def this(radius: Double) { this.r = radius }
          |  def area(): Double = r * r
          |  static def main(args: String[]): String = new Circle(2.0).area().toString()
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("4.0") == result)
    }

    it("reads a static field without Class::") {
      val result = shell.run(
        """
          |class C {
          |  static val n: Int = 42
          |public:
          |  static def get(): Int = n
          |  static def main(args: String[]): Int = get()
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(42) == result)
    }

    it("assigns an instance field without this.") {
      val result = shell.run(
        """
          |class Counter {
          |  var count: Int
          |public:
          |  def this() { this.count = 0 }
          |  def inc(): void { count = count + 1 }
          |  def value(): Int = count
          |  static def main(args: String[]): Int {
          |    val c = new Counter()
          |    c.inc()
          |    c.inc()
          |    c.inc()
          |    return c.value()
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(3) == result)
    }

    it("lets a local variable shadow a field of the same name") {
      val result = shell.run(
        """
          |class S {
          |  val x: Int
          |public:
          |  def this() { this.x = 100 }
          |  def f(): Int { val x = 5; return x }
          |  static def main(args: String[]): Int = new S().f()
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(5) == result)
    }

    it("still rejects an instance field referenced from a static method") {
      val result = shell.run(
        """
          |class C {
          |  val r: Int
          |public:
          |  def this() { this.r = 1 }
          |  static def bad(): Int = r
          |  static def main(args: String[]): Int = bad()
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }
  }
}
