package onion.compiler.tools

import onion.tools.Shell

/**
 * An empty collection literal (`[]` / `[:]`) in an `if`/`else` or `select`
 * BRANCH is target-typed from the expected result type (issue #300). Previously
 * the empty literal there erased to `Object`, so the branch join (`Object` vs
 * `List[Int]`) failed with E0000. The expected type is now threaded into each
 * branch, mirroring argument and block-trailing positions. When no expected type
 * is present, branch typing is unchanged (a plain `if b { 1 } else { 2 }` still
 * infers `Int`), and a genuine branch type mismatch is still an error.
 */
class BranchEmptyLiteralTargetTypeSpec extends AbstractShellSpec {
  describe("empty literal in if/else and select branches") {
    it("if/else method-body: empty list branch target-types") {
      assert(Shell.Success(0) == shell.run(
        "def f(b: Boolean): List[Int] = if b { [] } else { [1] }\n" +
          "def main(args: String[]): Int { return f(true).size() }", "None", Array()))
      assert(Shell.Success(1) == shell.run(
        "def f(b: Boolean): List[Int] = if b { [] } else { [1] }\n" +
          "def main(args: String[]): Int { return f(false).size() }", "None", Array()))
    }

    it("select method-body: empty list branch target-types") {
      assert(Shell.Success(0) == shell.run(
        "def g(n: Int): List[Int] = select n {\n  case 0: []\n  else: [1]\n}\n" +
          "def main(args: String[]): Int { return g(0).size() }", "None", Array()))
      assert(Shell.Success(1) == shell.run(
        "def g(n: Int): List[Int] = select n {\n  case 0: []\n  else: [1]\n}\n" +
          "def main(args: String[]): Int { return g(9).size() }", "None", Array()))
    }

    it("if/else: empty map branch target-types") {
      assert(Shell.Success(0) == shell.run(
        "def h(b: Boolean): Map[String, Int] = if b { [:] } else { [\"a\": 1] }\n" +
          "def main(args: String[]): Int { return h(true).size() }", "None", Array()))
      assert(Shell.Success(1) == shell.run(
        "def h(b: Boolean): Map[String, Int] = if b { [:] } else { [\"a\": 1] }\n" +
          "def main(args: String[]): Int { return h(false).size() }", "None", Array()))
    }

    it("else-if chain: empty branch target-types") {
      assert(Shell.Success(0) == shell.run(
        "def f(n: Int): List[Int] = if n == 0 { [] } else if n == 1 { [1] } else { [1, 2] }\n" +
          "def main(args: String[]): Int { return f(0).size() }", "None", Array()))
      assert(Shell.Success(2) == shell.run(
        "def f(n: Int): List[Int] = if n == 0 { [] } else if n == 1 { [1] } else { [1, 2] }\n" +
          "def main(args: String[]): Int { return f(5).size() }", "None", Array()))
    }

    it("val-annotated if/else: empty branch target-types") {
      assert(Shell.Success(0) == shell.run(
        "def main(args: String[]): Int { val o: List[Int] = if true { [] } else { [1] }\n return o.size() }",
        "None", Array()))
    }

    it("a range value from the non-empty branch is usable (a..b)") {
      assert(Shell.Success(6) == shell.run(
        "def f(b: Boolean): List[Int] = if b { [] } else { [1, 2, 3] }\n" +
          "def main(args: String[]): Int {\n" +
          "  var total: Int = 0\n" +
          "  foreach i: Int in 0..2 { total = total + f(false).get(i) }\n" +
          "  return total\n}", "None", Array()))
    }

    it("no expected type still infers Int (behaviour unchanged)") {
      assert(Shell.Success(11) == shell.run(
        "def main(args: String[]): Int { val x = if true { 1 } else { 2 }\n return x + 10 }", "None", Array()))
    }

    it("a genuine branch type mismatch is still an error") {
      assert(Shell.Failure(-1) == shell.run(
        "def f(b: Boolean): List[Int] = if b { \"hello\" } else { [1] }\n" +
          "def main(args: String[]): Int { return f(true).size() }", "None", Array()))
    }

    it("statement-position select with mixed value/void branches still works (#297)") {
      assert(Shell.Success(0) == shell.run(
        "def f(n: Int): void {\n" +
          "  select n {\n" +
          "    case 0: IO::println(\"zero\")\n" +
          "    case 1: 42\n" +
          "    else: IO::println(\"other\")\n" +
          "  }\n}\n" +
          "def main(args: String[]): Int { f(0)\n f(1)\n f(2)\n return 0 }", "None", Array()))
    }

    it("exhaustive sealed select still type-checks") {
      assert(Shell.Success(13) == shell.run(
        "sealed interface Shape\nrecord Circle(r: Int) <: Shape\nrecord Square(s: Int) <: Shape\n" +
          "def area(sh: Shape): Int = select sh {\n" +
          "  case c is Circle: c.r() * c.r()\n" +
          "  case s is Square: s.s() * s.s()\n}\n" +
          "def main(args: String[]): Int { return area(new Circle(2)) + area(new Square(3)) }", "None", Array()))
    }

    it("non-exhaustive sealed select is still rejected (E0042)") {
      assert(Shell.Failure(-1) == shell.run(
        "sealed interface Shape\nrecord Circle(r: Int) <: Shape\nrecord Square(s: Int) <: Shape\n" +
          "def area(sh: Shape): Int = select sh {\n  case c is Circle: c.r()\n}\n" +
          "def main(args: String[]): Int { return area(new Circle(2)) }", "None", Array()))
    }
  }
}
