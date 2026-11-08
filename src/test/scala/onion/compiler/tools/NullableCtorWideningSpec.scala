package onion.compiler.tools

import onion.tools.Shell

/** `new R(5)` widens the Int before boxing into a `Long?`/`Double?` slot (#2697). */
class NullableCtorWideningSpec extends AbstractShellSpec {
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

  describe("an Int constructor argument for a nullable wider numeric") {
    it("record R(x: Long?) takes new R(5)") {
      assert(Shell.Success(5L) == run("record R(x: Long?)", "Long", "(new R(5)).x() ?: 0L"))
    }
    it("record R(x: Double?) takes new R(5)") {
      assert(Shell.Success(5.0) == run("record R(x: Double?)", "Double", "(new R(5)).x() ?: 0.0"))
    }
    it("a class constructor with a Long? parameter takes an Int") {
      val c = "class C {\npublic:\n  val v: Long?\n  def this(v: Long?) { this.v = v }\n}"
      assert(Shell.Success(7L) == run(c, "Long", "(new C(7)).v ?: 0L"))
    }
    it("new R(5L) and new R(null) still work") {
      assert(Shell.Success(5L) == run("record R(x: Long?)", "Long", "(new R(5L)).x() ?: 0L"))
      assert(Shell.Success(-1L) == run("record R(x: Long?)", "Long", "(new R(null)).x() ?: -1L"))
    }
  }
}
