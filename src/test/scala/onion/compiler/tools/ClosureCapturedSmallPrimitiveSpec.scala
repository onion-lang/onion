package onion.compiler.tools

import onion.tools.Shell

/**
 * A closure-captured, reassigned `Boolean`/`Byte`/`Short`/`Char` local lives in
 * a box. The box was an `ObjectBox` whose value slot is a reference, but the
 * assigned value is an int on the JVM stack, so the method failed JVM
 * verification when it first ran (#1939). The `foreach` loop variable is the
 * natural way to hit it: it is reassigned on every iteration.
 */
class ClosureCapturedSmallPrimitiveSpec extends AbstractShellSpec {
  describe("a reassigned Boolean/Byte/Short/Char captured by a lambda") {
    it("a Boolean foreach variable can be captured") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def f(xs: List[String]): Int {
          |    var total = 0
          |    foreach b: Boolean in [true, false] {
          |      total += xs.filter { x -> x.isEmpty() == b }.size
          |    }
          |    return total
          |  }
          |  static def main(args: String[]): Int {
          |    return f(["", "a", "bb"])
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(3) == result)
    }

    it("a var of each small primitive type is shared with the lambda that mutates it") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    var flag: Boolean = false
          |    var small: Byte = 1 as Byte
          |    var mid: Short = 2 as Short
          |    var ch: Char = 'a'
          |    val bump: () -> Int = () -> {
          |      flag = true
          |      small = 3 as Byte
          |      mid = 4 as Short
          |      ch = 'z'
          |      return 0
          |    }
          |    bump()
          |    return "" + flag + small + mid + ch
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("true34z") == result)
    }
  }
}
