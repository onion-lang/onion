package onion.compiler.tools

import onion.tools.Shell

/**
 * Arrays whose element type is nullable (`T?[]`). The component is a NullableType, not a
 * plain ClassType, which used to crash member-selection accessibility checking
 * (`arr.length` on a `String?[]` raised an internal compiler error). Element access and
 * the `length` property must both work.
 */
class NullableArraySpec extends AbstractShellSpec {
  describe("nullable-element arrays (T?[])") {
    it("reads .length without an ICE (regression)") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val arr = new String?[3]
          |    arr[0] = "x"
          |    return arr[0] + "/" + arr.length
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("x/3") == result)
    }

    it("leaves unset nullable elements as null") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val arr = new String?[2]
          |    if arr[0] == null { return "null" } else { return "set" }
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("null") == result)
    }
  }
}
