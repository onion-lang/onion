package onion.compiler.tools

import onion.tools.Shell

class AssertSpec extends AbstractShellSpec {
  describe("Assert module") {
    describe("equals") {
      it("passes for equal strings") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    Assert::equals("hello", "hello");
            |    return "passed";
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("passed") == result)
      }

      it("passes for equal integers") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    Assert::equals(42, 42);
            |    return "passed";
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("passed") == result)
      }

      it("throws for unequal values") {
        val result = shell.run(
          """
            |import { java.lang.AssertionError; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    try {
            |      Assert::equals("a", "b");
            |      return "no error";
            |    } catch e: AssertionError {
            |      return "caught";
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("caught") == result)
      }

      it("passes for both null") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val a: String = null;
            |    val b: String = null;
            |    Assert::equals(a, b);
            |    return "passed";
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("passed") == result)
      }
    }

    describe("notEquals") {
      it("passes for different values") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    Assert::notEquals("a", "b");
            |    return "passed";
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("passed") == result)
      }

      it("throws for equal values") {
        val result = shell.run(
          """
            |import { java.lang.AssertionError; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    try {
            |      Assert::notEquals("same", "same");
            |      return "no error";
            |    } catch e: AssertionError {
            |      return "caught";
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("caught") == result)
      }
    }

    describe("notNull") {
      it("passes for non-null value") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    Assert::notNull("value");
            |    return "passed";
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("passed") == result)
      }

      it("throws for null value") {
        val result = shell.run(
          """
            |import { java.lang.AssertionError; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    try {
            |      val x: String = null;
            |      Assert::notNull(x);
            |      return "no error";
            |    } catch e: AssertionError {
            |      return "caught";
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("caught") == result)
      }
    }

    describe("isNull") {
      it("passes for null value") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val x: String = null;
            |    Assert::isNull(x);
            |    return "passed";
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("passed") == result)
      }

      it("throws for non-null value") {
        val result = shell.run(
          """
            |import { java.lang.AssertionError; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    try {
            |      Assert::isNull("not null");
            |      return "no error";
            |    } catch e: AssertionError {
            |      return "caught";
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("caught") == result)
      }
    }

    describe("isTrue") {
      it("passes for true condition") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    Assert::isTrue(1 < 2);
            |    return "passed";
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("passed") == result)
      }

      it("throws for false condition") {
        val result = shell.run(
          """
            |import { java.lang.AssertionError; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    try {
            |      Assert::isTrue(1 > 2);
            |      return "no error";
            |    } catch e: AssertionError {
            |      return "caught";
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("caught") == result)
      }
    }

    describe("isFalse") {
      it("passes for false condition") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    Assert::isFalse(1 > 2);
            |    return "passed";
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("passed") == result)
      }

      it("throws for true condition") {
        val result = shell.run(
          """
            |import { java.lang.AssertionError; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    try {
            |      Assert::isFalse(1 < 2);
            |      return "no error";
            |    } catch e: AssertionError {
            |      return "caught";
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("caught") == result)
      }
    }

    describe("fail") {
      it("always throws") {
        val result = shell.run(
          """
            |import { java.lang.AssertionError; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    try {
            |      Assert::fail("intentional failure");
            |      return "no error";
            |    } catch e: AssertionError {
            |      return "caught";
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("caught") == result)
      }
    }

    describe("custom messages") {
      it("uses custom message in equals") {
        val result = shell.run(
          """
            |import { java.lang.AssertionError; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    try {
            |      Assert::equals("a", "b", "custom message");
            |      return "no error";
            |    } catch e: AssertionError {
            |      return e.getMessage();
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("custom message") == result)
      }
    }
  }
}
