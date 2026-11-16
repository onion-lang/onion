package onion.compiler.tools

import onion.compiler.parser.{JJOnionParser, OnionParser}
import onion.tools.Shell

import java.io.StringReader

/**
 * `{ -> body }` is a zero-parameter lambda wherever a value is expected, not only as a
 * trailing lambda (#3070). Before, `twice({ -> "d" })` was a syntax error at the `->`, so a
 * call taking two lambdas (`opt.fold(ifEmpty, ifPresent)`) could not use the arrow form for
 * its first one. A block never starts with `->`, so no program that parsed before changes
 * meaning: `{ x -> x }` and `{ 3 }` in parentheses keep their old parse.
 */
class ArrowLambdaAsValueSpec extends AbstractShellSpec {

  private def run(src: String, name: String): Shell.Result = shell.run(src, name, Array())

  private def parity(src: String): Unit = {
    val fast = OnionParser.parse(src)
    val jj = new JJOnionParser(new StringReader(src)).unit()
    assert(fast == jj, "fast-path AST diverged from the JavaCC AST")
  }

  describe("`{ -> body }` inside call parentheses") {
    it("is a zero-parameter lambda, like the trailing form") {
      val src =
        """
          |class Test {
          |public:
          |  static def twice(f: () -> String): String = f() + f()
          |  static def main(args: String[]): Int {
          |    val a = twice({ -> "d" })
          |    val b = twice { -> "b" }
          |    return if a == "dd" && b == "bb" { 1 } else { 0 }
          |  }
          |}
          |""".stripMargin
      parity(src)
      assert(Shell.Success(1) == run(src, "ArrowInParens.on"))
    }

    it("works as the first of two lambda arguments, with a multi-line body") {
      val src =
        """
          |class Test {
          |public:
          |  static def fold(present: Boolean, ifEmpty: () -> Int, ifPresent: (Int) -> Int): Int =
          |    if present { ifPresent(20) } else { ifEmpty() }
          |  static def main(args: String[]): Int {
          |    val none = fold(false, { ->
          |      val base = 40
          |      base + 2
          |    }, { n -> n + 1 })
          |    val some = fold(true, { -> 0 }, { n -> n + 1 })
          |    return none * 100 + some
          |  }
          |}
          |""".stripMargin
      parity(src)
      assert(Shell.Success(4221) == run(src, "ArrowTwoLambdas.on"))
    }

    it("is a lambda as an initializer and as a constructor argument") {
      val src =
        """
          |class Box {
          |  val f: () -> Int
          |public:
          |  def this(g: () -> Int) { f = g }
          |  def get(): Int = f()
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val h: () -> Int = { -> 7 }
          |    val box = new Box({ -> 30 })
          |    return h() + box.get()
          |  }
          |}
          |""".stripMargin
      parity(src)
      assert(Shell.Success(37) == run(src, "ArrowInitializer.on"))
    }
  }

  describe("braces that do not start with `->`") {
    it("keep their old meaning in parentheses and as statements") {
      val src =
        """
          |class Test {
          |public:
          |  static def ap(f: (Int) -> Int): Int = f(4)
          |  static def id(n: Int): Int = n
          |  static def main(args: String[]): Int {
          |    var total = 0
          |    {
          |      total = total + 1
          |    }
          |    return ap({ x -> x * 10 }) + id({ 3 }) + total
          |  }
          |}
          |""".stripMargin
      parity(src)
      assert(Shell.Success(44) == run(src, "BraceNoArrow.on"))
    }
  }
}
