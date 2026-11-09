package onion.compiler.tools

import onion.tools.Shell

/** #2717: only a method with the `Object` signature replaces a record's generated
  * `toString`/`hashCode`/`equals`; an overload leaves the generated one in place. */
class RecordObjectMethodOverloadSpec extends AbstractShellSpec {
  private def run(body: String, expr: String): Shell.Result =
    shell.run(
      s"""
         |record R(name: String) {
         |public:
         |$body
         |}
         |class Test {
         |public:
         |  static def main(args: String[]): String {
         |    return $expr
         |  }
         |}
         |""".stripMargin, "RecordObjectMethodOverload.on", Array())

  describe("a record body method named like an Object method") {
    it("toString(n: Int) keeps the generated toString()") {
      assert(run("  def toString(n: Int): Int = n", "\"\" + new R(\"a\")") == Shell.Success("R(name=a)"))
    }
    it("toString(n: Int) is still callable") {
      assert(run("  def toString(n: Int): Int = n", "\"\" + new R(\"a\").toString(3)") == Shell.Success("3"))
    }
    it("hashCode(x: Int) keeps the generated hashCode()") {
      assert(run("  def hashCode(x: Int): Int = x",
        "\"\" + (new R(\"a\").hashCode() == new R(\"a\").hashCode())") == Shell.Success("true"))
    }
    it("equals(o: R) keeps the generated equals(Object)") {
      assert(run("  def equals(o: R): Int = 1",
        "\"\" + (new R(\"a\") as Object).equals(new R(\"a\"))") == Shell.Success("true"))
    }
    it("an explicit toString() override still wins") {
      assert(run("  override def toString(): String = \"custom\"", "\"\" + new R(\"a\")") == Shell.Success("custom"))
    }
  }
}
