package onion.compiler.tools

import onion.tools.Shell

/**
 * A block lambda with an early `return <value>` also returns its last expression.
 * It used to evaluate that expression and return null instead (#3025).
 */
class ClosureTrailingValueSpec extends AbstractShellSpec {
  private def run(body: String): Shell.Result =
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
      Array()
    )

  describe("a block lambda with an early return") {
    it("returns its last expression on the other path (Int)") {
      val result = run(
        """    val n: (Boolean) -> Int = (c) -> {
          |      if c { return 5 }
          |      40
          |    }
          |    return n(false) + n(true) * 100""".stripMargin)
      assert(Shell.Success(540) == result)
    }

    it("returns its last expression on the other path (String)") {
      val result = run(
        """    val s: (Boolean) -> String = (c) -> {
          |      if c { return "early" }
          |      "late"
          |    }
          |    return s(false).length() * 10 + s(true).length()""".stripMargin)
      assert(Shell.Success(45) == result)
    }

    it("types the last expression against the expected return type (Object)") {
      val result = run(
        """    val describe: (Int) -> Object = (x) -> {
          |      if x == 0 { return "zero" }
          |      x
          |    }
          |    return (describe(0) as String).length() * 10 + (describe(7) as Integer).intValue()""".stripMargin)
      assert(Shell.Success(47) == result)
    }

    it("counts the last expression when the return type is inferred") {
      val result = run(
        """    val xs = [1, -2, 3]
          |    val zs = xs.map { x ->
          |      if x < 0 { return "neg" }
          |      x * 10
          |    }
          |    return zs.size + (if zs.toString() == "[10, neg, 30]" { 100 } else { 0 }) + 1000""".stripMargin)
      assert(Shell.Success(1103) == result)
    }

    it("pins a generic call in the last expression from the expected type") {
      val result = run(
        """    val r: (Boolean) -> onion.Result[Int, String] = (c) -> {
          |      if c { return onion.Result::err("bad") }
          |      onion.Result::ok(3)
          |    }
          |    return r(false).getOrElse(0) + (if r(true).isErr() { 10 } else { 0 })""".stripMargin)
      assert(Shell.Success(13) == result)
    }

    it("returns a trailing if/else") {
      val result = run(
        """    val d: (Boolean) -> Int = (c) -> {
          |      if c { return 1 }
          |      if c { 2 } else { 3 }
          |    }
          |    return d(false) + 1 + d(true) * 10""".stripMargin)
      assert(Shell.Success(14) == result)
    }

    it("returns a trailing try/catch") {
      val result = run(
        """    val d: (Boolean) -> Int = (c) -> {
          |      if c { return 1 }
          |      try { Integer::parseInt("x") } catch e: NumberFormatException { 6 }
          |    }
          |    return d(false) + d(true) * 10""".stripMargin)
      assert(Shell.Success(16) == result)
    }

    it("keeps a void last expression a statement") {
      val result = run(
        """    var hits = 0
          |    val f: (Boolean) -> Object = (c) -> {
          |      if c { return "x" }
          |      hits = hits + 1
          |    }
          |    val g: (Boolean) -> Object = (c) -> {
          |      if c { return "x" }
          |      IO::print("")
          |    }
          |    return (if f(false) == null { 1 } else { 0 }) + (if g(false) == null { 10 } else { 0 })""".stripMargin)
      assert(Shell.Success(11) == result)
    }

    it("still ends in a throw") {
      val result = run(
        """    val f: (Boolean) -> Int = (c) -> {
          |      if c { return 7 }
          |      throw new Exception("no")
          |    }
          |    return f(true)""".stripMargin)
      assert(Shell.Success(7) == result)
    }
  }
}
