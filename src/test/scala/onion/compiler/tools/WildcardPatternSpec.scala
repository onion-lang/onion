package onion.compiler.tools

import onion.tools.Shell

class WildcardPatternSpec extends AbstractShellSpec {
  describe("Wildcard pattern matching") {
    it("matches any value with wildcard pattern") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val x = 42;
          |    return select x {
          |      case 1: "one"
          |      case 2: "two"
          |      case _: "other"
          |    };
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("other") == result)
    }

    it("wildcard matches when no other pattern matches") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val s = "hello";
          |    return select s {
          |      case "foo": "matched foo"
          |      case "bar": "matched bar"
          |      case _: "default"
          |    };
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("default") == result)
    }

    it("wildcard as only pattern acts like else") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val x = 100;
          |    return select x {
          |      case _: "always matches"
          |    };
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("always matches") == result)
    }

    it("wildcard works with sealed interfaces") {
      val result = shell.run(
        """
          |sealed interface Result {}
          |record Success(value: String) <: Result;
          |record Error(message: String) <: Result;
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val r: Result = new Error("oops");
          |    return select r {
          |      case s is Success: s.value()
          |      case _: "not success"
          |    };
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("not success") == result)
    }

    it("wildcard can be combined with other patterns using comma") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val x = 5;
          |    return select x {
          |      case 1, 2, 3: "small"
          |      case 4, _: "four or more"
          |    };
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("four or more") == result)
    }
  }
}
