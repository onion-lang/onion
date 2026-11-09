package onion.compiler.tools

import onion.tools.Shell

/** #2717: only a method with the `Object` signature replaces a record's generated
  * `toString`/`hashCode`/`equals`; an overload leaves the generated one in place.
  * Any method named `copy` replaces the generated `copy`, as before. */
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
    it("equals(o: Object?) still replaces the generated equals") {
      assert(run("  override def equals(o: Object?): Boolean = true",
        "\"\" + (new R(\"a\") == new R(\"b\"))") == Shell.Success("true"))
    }
    it("equals(o: JObject), an alias of Object, still replaces it") {
      val src =
        """
          |import { java.lang.Object as JObject; }
          |record R(name: String) {
          |public:
          |  override def equals(o: JObject): Boolean = true
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return "" + (new R("a") == new R("b"))
          |  }
          |}
          |""".stripMargin
      assert(shell.run(src, "RecordObjectMethodAlias.on", Array()) == Shell.Success("true"))
    }
    it("equals(o: T) in a generic record (erases to Object) still replaces it") {
      val src =
        """
          |record R[T](name: T) {
          |public:
          |  def equals(o: T): Boolean = true
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return "" + (new R("a") == new R("b"))
          |  }
          |}
          |""".stripMargin
      assert(shell.run(src, "RecordObjectMethodGeneric.on", Array()) == Shell.Success("true"))
    }
    it("a user copy() still replaces the generated copy, as on main") {
      val src =
        """
          |record R(x: Int) {
          |public:
          |  def copy(): R = new R(99)
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return "" + new R(1).copy()
          |  }
          |}
          |""".stripMargin
      assert(shell.run(src, "RecordUserCopy.on", Array()) == Shell.Success("R(x=99)"))
    }
    it("a user copy(v: Object) beside a String component replaces the generated copy") {
      assert(run("  def copy(v: Object): String = \"user\"", "new R(\"a\").copy(\"b\")") == Shell.Success("user"))
    }
  }
}
