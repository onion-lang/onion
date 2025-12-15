package onion.compiler.tools

import onion.tools.Shell
import org.scalatest._

class StringInterpolationSpec extends AbstractShellSpec {
  describe("string interpolation") {
    it("should interpolate simple expressions") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String = "The sum of #{5} and #{10} is #{5 + 10}"
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("The sum of 5 and 10 is 15") == result)
    }

    it("should interpolate variables in methods") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val x: Int = 5
          |    val y: Int = 10
          |    return "The sum of #{x} and #{y} is #{x + y}"
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("The sum of 5 and 10 is 15") == result)
    }

    it("should handle empty strings between interpolations") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a: Int = 1
          |    val b: Int = 2
          |    return "#{a}#{b}"
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("12") == result)
    }

    it("should handle string interpolation with no expressions") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String = "No interpolation here"
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("No interpolation here") == result)
    }

    it("should handle multiple interpolations") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a: Int = 1
          |    val b: Int = 2
          |    val c: Int = 3
          |    return "a=#{a}, b=#{b}, c=#{c}, sum=#{a+b+c}"
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("a=1, b=2, c=3, sum=6") == result)
    }
  }
}
