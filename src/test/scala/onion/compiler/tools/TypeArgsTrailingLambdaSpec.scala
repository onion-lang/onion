package onion.compiler.tools

import onion.compiler.parser.{JJOnionParser, OnionParser}
import onion.tools.Shell

import java.io.StringReader

/**
 * A trailing lambda may follow explicit type arguments with no `()` in between, as it may
 * follow a bare method name (#3200): `Comparator::nullsFirst[String] { a, b -> ... }` was a
 * syntax error while `Comparator::nullsFirst[String]((a, b) -> ...)` worked. In a condition,
 * an arrow-less `{` after `xs[i]` is still the statement block, not a lambda.
 */
class TypeArgsTrailingLambdaSpec extends AbstractShellSpec {

  private def runMain(body: String, name: String, prelude: String = ""): Shell.Result =
    shell.run(
      s"""
         |import { java.util.Comparator }
         |$prelude
         |class Test {
         |public:
         |  static def main(args: String[]): Int {
         |$body
         |  }
         |}
         |""".stripMargin,
      name,
      Array()
    )

  describe("a trailing lambda right after explicit type arguments") {
    it("is the only argument of a static call") {
      val r = runMain(
        """    val c = Comparator::nullsFirst[String] { a, b -> a.compareTo(b) }
          |    return c.compare("a", "b")""".stripMargin,
        "TypeArgsTrailingStatic.on")
      assert(Shell.Success(-1) == r)
    }

    it("works with a nullable type argument") {
      val r = runMain(
        """    val c = Comparator::nullsFirst[String?] { a, b -> (a ?: "").compareTo(b ?: "") }
          |    return c.compare(null, "b")""".stripMargin,
        "TypeArgsTrailingNullable.on")
      assert(Shell.Success(-1) == r)
    }

    it("works on an unqualified, an instance, a safe and a super call") {
      val prelude =
        """class Base {
          |public:
          |  def wrap[T](f: () -> T): Int = 100
          |}
          |class Sub extends Base {
          |public:
          |  override def wrap[T](f: () -> T): Int = 1 + super.wrap[T] { -> f() }
          |  static def twice[T](f: () -> T): List[T] = [f(), f()]
          |}""".stripMargin
      val r = runMain(
        """    val s = new Sub()
          |    val n1 = s.wrap[String] { -> "x" }
          |    val n2 = s?.wrap[String] { "y" }
          |    return n1 + (n2 ?: 0) + Sub::twice[Int] { -> 7 }.size""".stripMargin,
        "TypeArgsTrailingForms.on", prelude)
      assert(Shell.Success(204) == r)
    }

    it("leaves an index in a condition followed by its block alone") {
      val r = runMain(
        """    val xs = [true, false]
          |    var n = 0
          |    if xs[0] { n = n + 1 }
          |    while xs[n] { n = n + 1 }
          |    return n""".stripMargin,
        "TypeArgsTrailingCondition.on")
      assert(Shell.Success(1) == r)
    }
  }

  describe("both parsers read it the same way") {
    val snippets = Seq(
      """val c = Comparator::nullsFirst[String] { a, b -> a.compareTo(b) }""",
      """val c = java.util.Comparator::nullsFirst[String] { a, b -> a.compareTo(b) }""",
      """val e = f[String] { "y" }""",
      """val e = o.m[String] { -> "x" }""",
      """val e = o?.m[String] { x -> x }""",
      """val e = o.m[String](1) { -> "x" }""",
      """if xs[i] { println(1) }""",
      """if xs[i] { x -> x } == null { println(1) }"""
    )
    snippets.foreach { code =>
      it(s"`$code`") {
        val fast = OnionParser.parse(code)
        val jj = new JJOnionParser(new StringReader(code)).unit()
        assert(fast == jj)
      }
    }
  }
}
