package onion.compiler.tools

import onion.tools.Shell

/**
 * Issue #297: a `select` used as a STATEMENT (its value unused) must behave
 * like an if/else statement — a mix of value-returning and void case branches
 * is allowed and the whole select is a void statement. In EXPRESSION position a
 * void branch where a value is required must STILL be rejected.
 */
class SelectStatementMixedBranchSpec extends AbstractShellSpec {
  describe("select in statement position (issue #297)") {

    it("(a) allows a mix of value-returning and void case branches") {
      val result = shell.run(
        """
          |class Test {
          |  val env: java.util.HashMap[String, Integer]
          |public:
          |  def this { this.env = new java.util.HashMap[String, Integer]() }
          |  def exec(n: Int): void {
          |    select n {
          |      case 1: env.put("a", 1)
          |      else:   IO::println("x")
          |    }
          |  }
          |  static def main(args: String[]): Int {
          |    val t = new Test();
          |    t.exec(1);
          |    t.exec(2);
          |    return 42;
          |  }
          |}
          |""".stripMargin,
        "SelectStmtMixed.on",
        Array()
      )
      assert(Shell.Success(42) == result)
    }

    it("(b) still rejects a void branch where a value is required (val x = select ...)") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val x = select 1 {
          |      case 1: 5
          |      else:   IO::println("")
          |    };
          |    return 0;
          |  }
          |}
          |""".stripMargin,
        "SelectExprVoid.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("(b2) still rejects a void branch in a returned select where a value is required") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def f(n: Int): Int {
          |    return select n {
          |      case 1: 5
          |      else:   IO::println("")
          |    };
          |  }
          |  static def main(args: String[]): Int {
          |    return f(1);
          |  }
          |}
          |""".stripMargin,
        "SelectReturnVoid.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("(c) an exhaustive value-select-as-expression still returns its value") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def classify(n: Int): String {
          |    return select n {
          |      case 1: "one"
          |      case 2: "two"
          |      else:   "many"
          |    };
          |  }
          |  static def main(args: String[]): String {
          |    return classify(1) + "," + classify(2) + "," + classify(9);
          |  }
          |}
          |""".stripMargin,
        "SelectExprValue.on",
        Array()
      )
      assert(Shell.Success("one,two,many") == result)
    }

    it("(d) an all-void statement select still works") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  def exec(n: Int): void {
          |    select n {
          |      case 1: IO::println("one")
          |      case 2: IO::println("two")
          |      else:   IO::println("many")
          |    }
          |  }
          |  static def main(args: String[]): Int {
          |    val t = new Test();
          |    t.exec(1);
          |    t.exec(2);
          |    t.exec(3);
          |    return 7;
          |  }
          |}
          |""".stripMargin,
        "SelectStmtAllVoid.on",
        Array()
      )
      assert(Shell.Success(7) == result)
    }

    it("(e) a non-exhaustive sealed select in statement position still errors (E0042)") {
      val result = shell.run(
        """
          |sealed interface Shape {}
          |record Circle(r: Int) <: Shape
          |record Square(s: Int) <: Shape
          |class Test {
          |public:
          |  static def area(sh: Shape): void {
          |    select sh {
          |      case c is Circle: IO::println("circle")
          |    }
          |  }
          |  static def main(args: String[]): Int {
          |    return 0;
          |  }
          |}
          |""".stripMargin,
        "SelectSealedNonExhaustive.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }
  }
}
