package onion.compiler.tools

import onion.tools.Shell

/**
 * A raw (unparameterized) Java collection such as `new ArrayList()` must be
 * assignable where a parameterized type like `List[String]` is expected, per
 * Java's raw-type erasure / unchecked-conversion rule. Previously the subtype
 * walk compared the raw actual's `List[E]` super against `List[String]` and the
 * uninstantiated `E` made it fail. `isSuperTypeForClass` now erases the expected
 * applied type to its raw form when the actual is a raw class.
 */
class RawGenericAssignabilitySpec extends AbstractShellSpec {
  describe("raw generic assignability") {
    it("returns a raw ArrayList where List[String] is the declared return type") {
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
      assert(Shell.Success("hello") == result)
    }

    it("passes a raw ArrayList to a List[String] parameter") {
      val result = shell.run(
        """
          |import { java.util.* }
          |class Test {
          |public:
          |  static def size(xs: List[String]): Int {
          |    return xs.size()
          |  }
          |  static def main(args: String[]): Int {
          |    val raw = new ArrayList()
          |    raw.add("a")
          |    raw.add("b")
          |    return size(raw)
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(2) == result)
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
