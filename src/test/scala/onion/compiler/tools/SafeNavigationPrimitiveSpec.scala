package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for issue #133/#135 fixes: ?. with primitive-returning methods
 * keeps its nullable type (no unbox on the null path), nullable values
 * are accepted where Object is expected, and onion.IO wins over
 * java.lang.IO (Java 25+).
 */
class SafeNavigationPrimitiveSpec extends AbstractShellSpec {

  describe("Safe navigation with primitive returns") {
    it("yields null instead of NPE when the target is null") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val s: String? = null
          |    val n = s?.length()
          |    val t: String? = "hello"
          |    val m = t?.length()
          |    return "n=" + n + ",m=" + m
          |  }
          |}
          |""".stripMargin,
        "SafeNavPrimitive.on",
        Array()
      )
      assert(Shell.Success("n=null,m=5") == result)
    }

    it("supports ?. on void-returning methods as a statement") {
      val result = shell.run(
        """
          |class Greeter {
          |  var out: StringBuffer
          |public:
          |  def this { this.out = new StringBuffer() }
          |  def hello(): void { this.out.append("hi") }
          |  def text(): String { return this.out.toString() }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val g: Greeter? = new Greeter()
          |    g?.hello()
          |    val none: Greeter? = null
          |    none?.hello()
          |    if g != null { return g.text() } else { return "?" }
          |  }
          |}
          |""".stripMargin,
        "SafeNavVoid.on",
        Array()
      )
      assert(Shell.Success("hi") == result)
    }

    it("rejects indexing on a nullable receiver instead of crashing") {
      val result = shell.run(
        """
          |import { java.util.* }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs: List? = new ArrayList()
          |    return "" + xs[0]
          |  }
          |}
          |""".stripMargin,
        "NullableIndexing.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("passes nullable values where Object is expected") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def fmt(o: Object): String { return "" + o }
          |  static def main(args: String[]): String {
          |    val s: String? = null
          |    return fmt(s?.length())
          |  }
          |}
          |""".stripMargin,
        "NullableToObject.on",
        Array()
      )
      assert(Shell.Success("null") == result)
    }
  }
}
