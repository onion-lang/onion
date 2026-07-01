package onion.compiler.tools

import onion.tools.Shell

/**
 * Onion forbids raw generic types: a generic type used without type arguments
 * (e.g. `new ArrayList()` or a `List` annotation) is a compile error (E0066).
 * An applied type such as `ArrayList[String]` is still assignable where
 * `List[String]` is expected, per the usual subtype rule.
 */
class RawGenericAssignabilitySpec extends AbstractShellSpec {
  describe("raw generic types are forbidden") {
    it("rejects a raw `new ArrayList()`") {
      val result = shell.run(
        """
          |import { java.util.* }
          |class Test {
          |public:
          |  static def make(): List[String] {
          |    val list = new ArrayList()
          |    list.add("hello")
          |    return list
          |  }
          |  static def main(args: String[]): String {
          |    return make().get(0)
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("rejects a raw `List` annotation") {
      val result = shell.run(
        """
          |import { java.util.* }
          |class Test {
          |public:
          |  static def size(xs: List): Int {
          |    return xs.size()
          |  }
          |  static def main(args: String[]): Int {
          |    val raw: List[String] = ["a", "b"]
          |    return size(raw)
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("accepts an applied ArrayList[String] where List[String] is expected") {
      val result = shell.run(
        """
          |import { java.util.* }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val list: List[String] = new ArrayList[String]()
          |    list.add("ok")
          |    return list.get(0)
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }
  }
}
