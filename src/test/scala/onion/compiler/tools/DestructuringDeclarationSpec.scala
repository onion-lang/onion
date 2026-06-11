package onion.compiler.tools

import onion.tools.Shell

/**
 * Destructuring declarations: val/var (a, b) = record binds positional
 * components; arity mismatches report E0046 and non-records E0047.
 */
class DestructuringDeclarationSpec extends AbstractShellSpec {

  describe("Destructuring declarations") {
    it("binds record components positionally") {
      val result = shell.run(
        """
          |record Point(x: Int, y: Int)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p = new Point(1, 2)
          |    val (a, b) = p
          |    return "" + a + "," + b
          |  }
          |}
          |""".stripMargin,
        "DestructureVal.on",
        Array()
      )
      assert(Shell.Success("1,2") == result)
    }

    it("var destructuring yields mutable bindings") {
      val result = shell.run(
        """
          |record Point(x: Int, y: Int)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    var (mx, my) = new Point(7, 8)
          |    mx = mx + 1
          |    return "" + mx + "," + my
          |  }
          |}
          |""".stripMargin,
        "DestructureVar.on",
        Array()
      )
      assert(Shell.Success("8,8") == result)
    }

    it("val destructuring bindings reject reassignment") {
      val result = shell.run(
        """
          |record Point(x: Int, y: Int)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val (a, b) = new Point(1, 2)
          |    a = 5
          |    return "no"
          |  }
          |}
          |""".stripMargin,
        "DestructureValImmutable.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("reports E0046 on arity mismatch") {
      val result = shell.run(
        """
          |record Point(x: Int, y: Int)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val (a, b, c) = new Point(1, 2)
          |    return "no"
          |  }
          |}
          |""".stripMargin,
        "DestructureArity.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("reports an error for non-record initializers") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val (a, b) = "not a record"
          |    return "no"
          |  }
          |}
          |""".stripMargin,
        "DestructureNonRecord.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("evaluates the initializer exactly once") {
      val result = shell.run(
        """
          |record Point(x: Int, y: Int)
          |class Test {
          |  static var count: Int
          |public:
          |  static def make(): Point {
          |    Test::count = Test::count + 1
          |    return new Point(3, 4)
          |  }
          |  static def main(args: String[]): String {
          |    val (a, b) = Test::make()
          |    return "" + a + b + Test::count
          |  }
          |}
          |""".stripMargin,
        "DestructureOnce.on",
        Array()
      )
      assert(Shell.Success("341") == result)
    }
  }
}
