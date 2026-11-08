package onion.compiler.tools

import onion.tools.Shell

/** `val a: Long? = 5` widens the Int literal before boxing, as `val a: Long = 5` does (#2684). */
class NullableBoxedWideningSpec extends AbstractShellSpec {
  private def run(decl: String, ret: String, expr: String): Shell.Result =
    shell.run(
      s"""
         |class Test {
         |public:
         |  static def main(args: String[]): $ret {
         |    $decl
         |    return $expr
         |  }
         |}
         |""".stripMargin, "None", Array())

  describe("an Int assigned to a nullable wider numeric") {
    it("Long? takes an Int literal") {
      assert(Shell.Success(5L) == run("val a: Long? = 5", "Long", "a ?: 0L"))
    }
    it("Double? takes an Int literal") {
      assert(Shell.Success(5.0) == run("val d: Double? = 5", "Double", "d ?: 0.0"))
    }
    it("Long? takes an Int variable") {
      assert(Shell.Success(7L) == run("val i: Int = 7\n    val a: Long? = i", "Long", "a ?: 0L"))
    }
    it("a var Long? accepts an Int on reassignment") {
      assert(Shell.Success(3L) == run("var a: Long? = null\n    a = 3", "Long", "a ?: 0L"))
    }
  }
}
