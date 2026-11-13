package onion.compiler.tools

import onion.tools.Shell

/**
 * A `continue` or `break` inside an operand, while another operand is already
 * evaluated (`10 + (if c { continue } else { i })`), must compile and jump (#2932).
 * It crashed the compiler with I0000 in BytecodeGeneration: the values the enclosing
 * expression had pushed were still on the JVM stack at the jump.
 */
class LoopJumpInOperandSpec extends AbstractShellSpec {
  private def run(body: String, members: String = ""): Shell.Result =
    shell.run(
      s"""
         |class Test {
         |public:
         |$members
         |  static def main(args: String[]): Int {
         |$body
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  describe("a loop jump inside an operand") {
    it("continue as the right operand of +") {
      val result = run(
        """    var i: Int = 0
          |    var out: Int = 0
          |    while i < 4 {
          |      i = i + 1
          |      val s: Int = 10 + (if i == 2 { continue } else { i })
          |      out = out + s
          |    }
          |    return out""".stripMargin)
      assert(Shell.Success(38) == result)
    }

    it("break as the right operand of string concatenation") {
      val result = run(
        """    var acc = ""
          |    var i: Int = 0
          |    while i < 5 {
          |      i = i + 1
          |      acc = acc + ("v=" + (if i == 3 { break } else { i }))
          |    }
          |    return acc.length() * 10 + i""".stripMargin)
      assert(Shell.Success(63) == result)
    }

    it("continue inside a string interpolation") {
      val result = run(
        """    val sb = new StringBuilder()
          |    foreach i in 1..4 {
          |      sb.append("v=#{if i == 2 { continue } else { i }};")
          |    }
          |    return sb.toString().length()""".stripMargin)
      assert(Shell.Success(12) == result)
    }

    it("continue as an argument of an instance method") {
      val result = run(
        """    val b = new Test()
          |    var out: Int = 0
          |    foreach i in 1..4 {
          |      out = out + b.g(if i == 2 { continue } else { i })
          |    }
          |    return out""".stripMargin,
        "  def g(x: Int): Int = x * 2")
      assert(Shell.Success(16) == result)
    }

    it("break as the second argument of a static method") {
      val result = run(
        """    var out: Int = 0
          |    foreach i in 1..5 {
          |      out = out + add(100, if i == 3 { break } else { i })
          |    }
          |    return out""".stripMargin,
        "  static def add(a: Int, b: Int): Int = a + b")
      assert(Shell.Success(203) == result)
    }

    it("break as the argument of a function value") {
      val result = run(
        """    val f: Int -> Int = (x) -> x + 1
          |    var out: Int = 0
          |    foreach i in 1..5 {
          |      out = out + f(if i == 3 { break } else { i })
          |    }
          |    return out""".stripMargin)
      assert(Shell.Success(5) == result)
    }

    it("continue with long and double operands on the stack") {
      val result = run(
        """    var total: Long = 0L
          |    var d: Double = 0.0
          |    foreach i in 1..4 {
          |      total = total + (100L + (if i == 2 { continue } else { i as Long }))
          |      d = d + (0.5 + (if i == 3 { continue } else { 1.0 }))
          |    }
          |    return (total as Int) + (d as Int)""".stripMargin)
      assert(Shell.Success(311) == result)
    }

    it("a labeled break from an inner loop drops operands of both loops' expressions") {
      val result = run(
        """    var out: Int = 0
          |    outer: foreach i in 1..3 {
          |      foreach j in 1..3 {
          |        out = out + (1000 + (if i == 2 && j == 2 { break outer } else { j }))
          |      }
          |    }
          |    return out""".stripMargin)
      assert(Shell.Success(4007) == result)
    }

    it("continue inside try/finally runs the finally block") {
      val result = run(
        """    var out: Int = 0
          |    var fin: Int = 0
          |    foreach i in 1..3 {
          |      try {
          |        out = out + (10 + (if i == 2 { continue } else { i }))
          |      } finally {
          |        fin = fin + 1
          |      }
          |    }
          |    return out * 10 + fin""".stripMargin)
      assert(Shell.Success(243) == result)
    }

    it("a loop inside an operand's block keeps the outer operand") {
      val result = run(
        """    var k: Int = 0
          |    val v: Int = 7 + (if k == 0 {
          |      while true {
          |        k = k + 1
          |        if k == 3 { break }
          |      }
          |      k
          |    } else { 0 })
          |    return v""".stripMargin)
      assert(Shell.Success(10) == result)
    }
  }
}
