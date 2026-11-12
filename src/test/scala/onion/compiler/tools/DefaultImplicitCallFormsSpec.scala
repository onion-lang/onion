package onion.compiler.tools

import onion.tools.Shell

/**
 * A call the source does not spell as `m(...)` fills an omitted default too
 * (#2803): `v.total`, `a + b`, `v[i]`, `v[i] = x`, `a < b`, `a += b`. Before,
 * each compiled with the written arguments only, and the class failed to load
 * with VerifyError.
 */
class DefaultImplicitCallFormsSpec extends AbstractShellSpec {

  private def run(body: String, name: String): Shell.Result =
    shell.run(body.stripMargin, name, Array())

  describe("An omitted default in an implicit call form") {
    it("is filled for a call without parentheses") {
      val result = run(
        """
          |class V {
          |public:
          |  def this {}
          |  def total(extra: Int = 100): Int = extra + 1
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String = "" + new V().total + "," + new V().total()
          |}
          |""",
        "DefaultNoParens.on"
      )
      assert(Shell.Success("101,101") == result)
    }

    it("is filled for an operator method") {
      val result = run(
        """
          |class V {
          |public:
          |  val n: Int
          |  def this(n: Int) { this.n = n }
          |  def plus(o: V, extra: Int = 100): V = new V(n + o.n + extra)
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    var a = new V(1)
          |    val b = (a + new V(2)).n
          |    a += new V(3)
          |    return "" + b + "," + a.n
          |  }
          |}
          |""",
        "DefaultOperator.on"
      )
      assert(Shell.Success("103,104") == result)
    }

    it("is filled for index reads and writes") {
      val result = run(
        """
          |class V {
          |public:
          |  var last: Int
          |  def this { last = 0 }
          |  def get(i: Int, extra: Int = 5): Int = i + extra
          |  def set(i: Int, v: Int, extra: Int = 50): void { last = i + v + extra }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val v = new V()
          |    v[1] = 2
          |    return "" + v[3] + "," + v.last
          |  }
          |}
          |""",
        "DefaultIndex.on"
      )
      assert(Shell.Success("8,53") == result)
    }

    it("is filled for a comparison through compareTo") {
      val result = run(
        """
          |class V {
          |public:
          |  val n: Int
          |  def this(n: Int) { this.n = n }
          |  def compareTo(o: V, bias: Int = 10): Int = n - o.n - bias
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String = "" + (new V(5) < new V(1))
          |}
          |""",
        "DefaultCompare.on"
      )
      assert(Shell.Success("true") == result)
    }
  }
}
