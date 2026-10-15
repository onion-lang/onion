package onion.compiler.tools

import onion.tools.Shell

/**
 * A `case` with 2+ comma-joined alternatives where at least one is a type,
 * destructuring, regex or bare-binding pattern used to compile but miscompile:
 * only the last binding-introducing alternative's type reached codegen and the
 * exhaustiveness check, so the generated body unconditionally casts the scrutinee
 * to that one type -- throwing `ClassCastException` whenever the case actually
 * matched via a *different* alternative (another type pattern, or even a bare
 * wildcard/`_`, which matches anything regardless of the recorded binding's type).
 * Leaving every alternative but one out of such a comma list also made the
 * exhaustiveness check (falsely) report E0042 for the type that *was* listed.
 * Rejecting the whole shape at compile time (#2105) turns both into one diagnostic.
 */
class MultiTypePatternCommaSpec extends AbstractShellSpec {

  it("rejects two comma-joined type patterns with different names (E0100)") {
    val result = shell.run(
      """
        |enum Shape { case Circle(radius: Double); case Square(side: Double); case Triangle(base: Double, height: Double) }
        |class Test {
        |public:
        |  static def isRound(sh: Shape): Boolean {
        |    return select sh {
        |      case c is Circle: true
        |      case s is Square, t is Triangle: false
        |      else: false
        |    };
        |  }
        |  static def main(args: String[]): Int { return 0 }
        |}
        |""".stripMargin,
      "None",
      Array()
    )
    assert(Shell.Failure(-1) == result)
  }

  it("rejects the same binding name reused across comma-joined types (E0100), matching run/DatabaseSchema.on's old shape") {
    val result = shell.run(
      """
        |enum DataType { case IntType; case BigIntType; case BooleanType }
        |class Test {
        |public:
        |  static def isNumeric(d: DataType): Boolean {
        |    return select d {
        |      case x is IntType, x is BigIntType: true
        |      else: false
        |    };
        |  }
        |  static def main(args: String[]): Int { return 0 }
        |}
        |""".stripMargin,
      "None",
      Array()
    )
    assert(Shell.Failure(-1) == result)
  }

  it("a plain comma-joined value-pattern list (no type/destructuring pattern involved) still works") {
    val result = shell.run(
      """
        |class Test {
        |public:
        |  static def describe(n: Int): String {
        |    return select n {
        |      case 1, 2, 3: "small"
        |      else: "other"
        |    };
        |  }
        |  static def main(args: String[]): String {
        |    return describe(2) + "|" + describe(9);
        |  }
        |}
        |""".stripMargin,
      "None",
      Array()
    )
    assert(Shell.Success("small|other") == result)
  }

  it("rejects a type pattern comma-joined with a wildcard too (E0100) -- also unconditionally casts, discovered while testing this fix") {
    val result = shell.run(
      """
        |class Test {
        |public:
        |  static def describe(x: Object): String {
        |    return select x {
        |      case s is String, _: "str-or-anything:" + x
        |    };
        |  }
        |  static def main(args: String[]): String {
        |    return describe("hi") + "|" + describe(42);
        |  }
        |}
        |""".stripMargin,
      "None",
      Array()
    )
    assert(Shell.Failure(-1) == result)
  }

  it("rewriting as separate case clauses (the fix for the rejected shape) works correctly for every alternative") {
    val result = shell.run(
      """
        |enum Shape { case Circle(radius: Double); case Square(side: Double); case Triangle(base: Double, height: Double) }
        |class Test {
        |public:
        |  static def isRound(sh: Shape): Boolean {
        |    return select sh {
        |      case c is Circle: true
        |      case s is Square: false
        |      case t is Triangle: false
        |    };
        |  }
        |  static def main(args: String[]): String {
        |    return "" + isRound(new Square(2.0)) + "|" + isRound(new Triangle(1.0, 2.0)) + "|" + isRound(new Circle(1.0));
        |  }
        |}
        |""".stripMargin,
      "None",
      Array()
    )
    assert(Shell.Success("false|false|true") == result)
  }
}
