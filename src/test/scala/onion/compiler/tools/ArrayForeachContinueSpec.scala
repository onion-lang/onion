package onion.compiler.tools

import onion.tools.Shell

/**
 * `continue` inside `foreach` over an array must still advance to the next
 * element (#2940). Each program counts its iterations and returns -1 once the
 * count exceeds the array's length, so a loop that stops advancing fails fast
 * instead of hanging the suite.
 */
class ArrayForeachContinueSpec extends AbstractShellSpec {
  private def run(body: String, args: Array[String] = Array()): Shell.Result =
    shell.run(
      s"""
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |$body
        |  }
        |}
        |""".stripMargin,
      "None",
      args
    )

  describe("continue in foreach over an array") {
    it("skips one Int[] element and visits the rest") {
      val result = run(
        """
          |    val ys: Int[] = new Int[3]
          |    ys[0] = 1; ys[1] = 2; ys[2] = 3
          |    var guard = 0
          |    var sum = 0
          |    foreach y in ys {
          |      guard = guard + 1
          |      if guard > 10 { return -1 }
          |      if y == 2 { continue }
          |      sum = sum + y
          |    }
          |    return sum
          |""".stripMargin)
      assert(Shell.Success(4) == result)
    }

    it("skips matching args in main's String[]") {
      val result = run(
        """
          |    var guard = 0
          |    var n = 0
          |    foreach a in args {
          |      guard = guard + 1
          |      if guard > 10 { return -1 }
          |      if a == "x" { continue }
          |      n = n + 1
          |    }
          |    return n
          |""".stripMargin,
        Array("a", "x", "b"))
      assert(Shell.Success(2) == result)
    }

    it("advances the outer array loop on a labeled continue from an inner loop") {
      val result = run(
        """
          |    val ys: Int[] = new Int[3]
          |    ys[0] = 1; ys[1] = 2; ys[2] = 3
          |    var guard = 0
          |    var sum = 0
          |    outer: foreach y in ys {
          |      guard = guard + 1
          |      if guard > 10 { return -1 }
          |      foreach z in ys {
          |        if z == y { continue outer }
          |        sum = sum + 10
          |      }
          |      sum = sum + 100
          |    }
          |    return sum
          |""".stripMargin)
      // y=1: no inner additions; y=2: +10; y=3: +20; the outer tail never runs
      assert(Shell.Success(30) == result)
    }

    it("runs a finally once per element when continuing out of it") {
      val result = run(
        """
          |    val ys: Int[] = new Int[4]
          |    ys[0] = 1; ys[1] = 2; ys[2] = 3; ys[3] = 4
          |    var guard = 0
          |    var finals = 0
          |    foreach y in ys {
          |      guard = guard + 1
          |      if guard > 10 { return -1 }
          |      try {
          |        if y % 2 == 0 { continue }
          |      } finally {
          |        finals = finals + 1
          |      }
          |    }
          |    return finals
          |""".stripMargin)
      assert(Shell.Success(4) == result)
    }

    it("still visits every element when nothing continues") {
      val result = run(
        """
          |    val ys: Int[] = new Int[3]
          |    ys[0] = 5; ys[1] = 6; ys[2] = 7
          |    var sum = 0
          |    foreach y in ys { sum = sum + y }
          |    return sum
          |""".stripMargin)
      assert(Shell.Success(18) == result)
    }
  }
}
