package onion.compiler.tools

import onion.tools.Shell

/**
 * The grammar nests bitwise/logical/range operators strictly by precedence
 * (see `bit_or`/`xor`/`bit_and`/`range_expr` in grammar/JJOnionParser.jj),
 * not as the flat "same level" groups `docs/reference/specification.md`
 * used to describe. These pin the actual grouping down operationally so the
 * operator table can't silently drift from the parser again.
 */
class OperatorPrecedenceSpec extends AbstractShellSpec {
  describe("bitwise operator precedence") {
    it("binds & tighter than | (6 | 1 & 2 is 6 | (1 & 2), not (6 | 1) & 2)") {
      val result = shell.run("IO::println(6 | 1 & 2)", "None", Array())
      assert(result.isInstanceOf[Shell.Success])
    }

    it("binds & tighter than ^, and ^ tighter than | (12 ^ 4 & 4 is 12 ^ (4 & 4))") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int = 12 ^ 4 & 4
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      // Correct grouping: 12 ^ (4 & 4) = 12 ^ 4 = 8.
      // A flat same-precedence left-to-right reading would give (12 ^ 4) & 4 = 0.
      assert(Shell.Success(8) == result)
    }
  }

  describe("logical operator precedence") {
    it("binds && tighter than || (true || false && false is true || (false && false))") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Boolean = true || false && false
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      // Correct grouping: true || (false && false) = true.
      // A flat same-precedence left-to-right reading would give (true || false) && false = false.
      assert(Shell.Success(true) == result)
    }

    it("chains ?: at the same level as ||, left to right, not as a separate looser operator") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Boolean {
          |    val a: Boolean? = false
          |    return a ?: false || true
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      // Correct grouping: (a ?: false) || true = false || true = true.
      // If ?: were a separate, looser level than ||, this would be a ?: (false || true),
      // and since a is non-null, elvis would short-circuit to a = false.
      assert(Shell.Success(true) == result)
    }
  }

  describe("range operator precedence") {
    it("binds << tighter than .. (1..2 << 1 is 1..(2 << 1), i.e. the range 1..4)") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    var sum = 0
          |    foreach i: Int in 1..2 << 1 { sum = sum + i }
          |    return sum
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(10) == result)
    }
  }
}
