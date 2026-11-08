package onion.compiler.tools

import onion.tools.Shell

/** An Int argument for a `Long?` slot is widened at every argument site, not
 *  only `new R(5)` (#2701; #2684 and #2700 fixed two sites one at a time). */
class NullableArgSitesSpec extends AbstractShellSpec {
  private def run(decls: String, ret: String, expr: String): Shell.Result =
    shell.run(
      s"""
         |$decls
         |class Test {
         |public:
         |  static def main(args: String[]): $ret {
         |    return $expr
         |  }
         |}
         |""".stripMargin, "None", Array())

  describe("an Int argument for a Long? parameter") {
    it("a superclass constructor call widens it") {
      val d = "class P(val v: Long?)\nclass Q extends P(5)"
      assert(Shell.Success(5L) == run(d, "Long", "(new Q()).v ?: 0L"))
    }
    it("a this(...) delegation widens it") {
      val d = "class P {\npublic:\n  val v: Long?\n  def this(v: Long?) { this.v = v }\n  def this: this(5) { }\n}"
      assert(Shell.Success(5L) == run(d, "Long", "(new P()).v ?: 0L"))
    }
    it("a named copy widens it") {
      val d = "record N(a: Int, b: Long?)"
      assert(Shell.Success(9L) == run(d, "Long", "(new N(1, 2L).copy(b = 9)).b() ?: 0L"))
    }
    it("a constructor call with a lambda argument widens it") {
      val d = "record R(x: Long?, f: (Int) -> Int)"
      assert(Shell.Success(5L) == run(d, "Long", "(new R(5, (y) -> y + 1)).x() ?: 0L"))
    }
  }

  // super(..)/this(..) adapt their arguments the way `new` does, so a plain
  // primitive slot is widened too, not only a boxed one (panel on #2703).
  describe("an Int argument for a plain Long parameter") {
    it("a superclass constructor call widens it") {
      val d = "class P2(val v: Long)\nclass Q2 extends P2(5)"
      assert(Shell.Success(5L) == run(d, "Long", "(new Q2()).v"))
    }
    it("a this(...) delegation widens it") {
      val d = "class P {\npublic:\n  val v: Long\n  def this(v: Long) { this.v = v }\n  def this: this(5) { }\n}"
      assert(Shell.Success(5L) == run(d, "Long", "(new P()).v"))
    }
    it("a superclass constructor call with a Double slot widens it") {
      val d = "class P3(val v: Double)\nclass Q3 extends P3(2)"
      assert(Shell.Success(2.0) == run(d, "Double", "(new Q3()).v"))
    }
  }
}
