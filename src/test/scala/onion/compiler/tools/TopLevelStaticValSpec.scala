package onion.compiler.tools

import onion.tools.Shell

class TopLevelStaticValSpec extends AbstractShellSpec {
  describe("top-level static val / static var") {

    it("initializes a static Int val at the top level") {
      val result = shell.run(
        """
          |static val X: Int = 42
          |def main(args: String[]): Int = X
          |""".stripMargin,
        "None", Array()
      )
      assert(Shell.Success(42) == result)
    }

    it("initializes a static String val at the top level") {
      val result = shell.run(
        """
          |static val MSG: String = "hello"
          |def main(args: String[]): String = MSG
          |""".stripMargin,
        "None", Array()
      )
      assert(Shell.Success("hello") == result)
    }

    it("initializes a static val with a list literal") {
      val result = shell.run(
        """
          |import { java.util.List; }
          |static val ITEMS: List[String] = ["a", "b", "c"]
          |def main(args: String[]): Int = ITEMS.size()
          |""".stripMargin,
        "None", Array()
      )
      assert(Shell.Success(3) == result)
    }

    it("initializes a static val with enum constants") {
      val result = shell.run(
        """
          |import { java.util.List; }
          |enum Color { Red, Green, Blue }
          |static val PRIMARIES: List[Color] = [Color::Red, Color::Green, Color::Blue]
          |def main(args: String[]): Int = PRIMARIES.size()
          |""".stripMargin,
        "None", Array()
      )
      assert(Shell.Success(3) == result)
    }

    it("static val is accessible from a top-level function") {
      val result = shell.run(
        """
          |static val GREETING: String = "world"
          |static def greet(): String = "Hello, " + GREETING + "!"
          |def main(args: String[]): String = greet()
          |""".stripMargin,
        "None", Array()
      )
      assert(Shell.Success("Hello, world!") == result)
    }

    it("initializes a static var and allows assignment") {
      val result = shell.run(
        """
          |static var counter: Int = 10
          |static def bump(): void { counter = counter + 5 }
          |def main(args: String[]): Int {
          |  bump()
          |  return counter
          |}
          |""".stripMargin,
        "None", Array()
      )
      assert(Shell.Success(15) == result)
    }

    it("two static vals are independently initialized") {
      val result = shell.run(
        """
          |static val A: Int = 3
          |static val B: Int = 7
          |def main(args: String[]): Int = A + B
          |""".stripMargin,
        "None", Array()
      )
      assert(Shell.Success(10) == result)
    }
  }
}
