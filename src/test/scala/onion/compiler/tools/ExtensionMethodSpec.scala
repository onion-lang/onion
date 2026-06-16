package onion.compiler.tools

import onion.tools.Shell

class ExtensionMethodSpec extends AbstractShellSpec {
  describe("Extension methods") {
    it("can add methods to String") {
      val result = shell.run(
        """
          |extension String {
          |  def twice(): String {
          |    return this + this
          |  }
          |}
          |
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    val s: String = "hello"
          |    return s.twice()
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("hellohello") == result)
    }

    it("can add methods with parameters") {
      val result = shell.run(
        """
          |extension String {
          |  def repeat(n: Int): String {
          |    var result: String = ""
          |    var i: Int = 0
          |    while i < n {
          |      result = result + this
          |      i = i + 1
          |    }
          |    return result
          |  }
          |}
          |
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    return "ab".repeat(3)
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ababab") == result)
    }

    it("can add methods to Integer") {
      val result = shell.run(
        """
          |extension JInteger {
          |  def doubled(): Int {
          |    return this.intValue() * 2
          |  }
          |}
          |
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    val n: JInteger = JInteger::valueOf(21)
          |    return "" + n.doubled()
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("42") == result)
    }

    it("extension methods can call other methods on receiver") {
      val result = shell.run(
        """
          |extension String {
          |  def shout(): String {
          |    return this.toUpperCase() + "!"
          |  }
          |}
          |
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    return "hello".shout()
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("HELLO!") == result)
    }

    it("finds extension methods defined on receiver supertypes") {
      val result = shell.run(
        """
          |extension Object {
          |  def describe(): String {
          |    return "Object(" + this.toString() + ")"
          |  }
          |}
          |
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    return "hello".describe()
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("Object(hello)") == result)
    }

    it("supports a single-line expression-bodied method (closing brace on the same line)") {
      val result = shell.run(
        """
          |extension String { def shout(): String = self.toUpperCase() }
          |
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    return "hi".shout()
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("HI") == result)
    }

    it("lets a user extension method shadow a builtin Colls extension of the same name") {
      val result = shell.run(
        """
          |extension List[String] {
          |  def first(): String { return "user:" + this.get(0) }
          |}
          |
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    return ["A", "B"].first()
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("user:A") == result)
    }
  }
}
