package onion.compiler.tools

import onion.tools.Shell

class SafeNavigationNamedArgumentsSpec extends AbstractShellSpec {
  describe("Named arguments through safe navigation (?.)") {

    it("resolves a named-argument method call on a non-null receiver, out of declaration order") {
      val result = shell.run(
        """
          |class Greeter {
          |public:
          |  def this {}
          |  def greet(name: String, loud: Boolean): String = {
          |    if loud { name.toUpperCase() + "!" } else { name }
          |  }
          |}
          |
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    val g: Greeter? = new Greeter()
          |    return "" + g?.greet(loud = true, name = "hi")
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("HI!") == result)
    }

    it("short-circuits a named-argument method call to null on a null receiver") {
      val result = shell.run(
        """
          |class Greeter {
          |public:
          |  def this {}
          |  def greet(name: String, loud: Boolean): String = {
          |    if loud { name.toUpperCase() + "!" } else { name }
          |  }
          |}
          |
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    val g: Greeter? = null
          |    return "" + g?.greet(loud = true, name = "hi")
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("null") == result)
    }

    it("reports an error for an unknown parameter name in a named-argument safe-nav call") {
      val result = shell.run(
        """
          |class Greeter {
          |public:
          |  def this {}
          |  def greet(name: String, loud: Boolean): String = name
          |}
          |
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    val g: Greeter? = new Greeter()
          |    return "" + g?.greet(shout = true, name = "hi")
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }
  }

  describe("Record copy() sugar through safe navigation (?.)") {

    it("full-clones a record via zero-arg copy() on a non-null receiver") {
      val result = shell.run(
        """
          |record Point(x: Int, y: Int)
          |
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    val p: Point? = new Point(1, 2)
          |    return "" + p?.copy()
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("Point(x=1, y=2)") == result)
    }

    it("short-circuits zero-arg copy() to null on a null receiver") {
      val result = shell.run(
        """
          |record Point(x: Int, y: Int)
          |
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    val p: Point? = null
          |    return "" + p?.copy()
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("null") == result)
    }

    it("partially copies a record via named arguments on a non-null receiver") {
      val result = shell.run(
        """
          |record Point(x: Int, y: Int)
          |
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    val p: Point? = new Point(1, 2)
          |    return "" + p?.copy(y = 9)
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("Point(x=1, y=9)") == result)
    }

    it("short-circuits a named-argument partial copy to null on a null receiver") {
      val result = shell.run(
        """
          |record Point(x: Int, y: Int)
          |
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    val p: Point? = null
          |    return "" + p?.copy(y = 9)
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
