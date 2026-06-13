package onion.compiler.tools

import onion.tools.Shell

/**
 * Primitive-exact overload selection (Java most-specific tiebreak).
 *
 * `java.util.List` declares both `remove(int)` (remove by index) and
 * `remove(Object)` (remove by value). Calling `list.remove(1)` with an Onion
 * `Int` is applicable to both, and the subtype rule leaves them incomparable.
 * The resolver now prefers the candidate whose formal parameter is the exact
 * matching primitive, mirroring Java: `remove(1)` removes index 1.
 */
class PrimitiveOverloadSpec extends AbstractShellSpec {
  describe("primitive-exact overload selection") {
    it("selects remove(int) over remove(Object) for an Int argument") {
      val result = shell.run(
        """
          |import { java.util.* }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val list = new ArrayList[String]()
          |    list.add("a")
          |    list.add("b")
          |    list.add("c")
          |    list.remove(1)
          |    return list.size() + ":" + list.get(0) + list.get(1)
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      // remove(1) removes index 1 ("b"), leaving ["a", "c"]
      assert(Shell.Success("2:ac") == result)
    }

    it("removes by value when given a non-primitive Object") {
      val result = shell.run(
        """
          |import { java.util.* }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val list = new ArrayList[String]()
          |    list.add("a")
          |    list.add("b")
          |    list.add("c")
          |    list.remove("b")
          |    return list.size() + ":" + list.get(0) + list.get(1)
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      // remove("b") removes the value "b", leaving ["a", "c"]
      assert(Shell.Success("2:ac") == result)
    }

    it("resolves a user-defined primitive vs Object overload to the primitive") {
      val result = shell.run(
        """
          |class Over {
          |public:
          |  def pick(x: Int): String { return "int" }
          |  def pick(x: Object): String { return "object" }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val o = new Over()
          |    return o.pick(7)
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("int") == result)
    }
  }
}
