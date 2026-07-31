package onion.compiler.tools

import onion.tools.Shell

class WrapperClassesSpec extends AbstractShellSpec {
  describe("JInteger") {
    it("parses a string into an Int") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val i: Int = JInteger::parseInt("42");
          |    return "" + i;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("42") == result)
    }

    it("exposes MAX_VALUE and MIN_VALUE") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val max: Int = JInteger::MAX_VALUE;
          |    val min: Int = JInteger::MIN_VALUE;
          |    return max + "," + min;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("2147483647,-2147483648") == result)
    }
  }

  describe("JLong") {
    it("parses a string into a Long") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val l: Long = JLong::parseLong("1234567890");
          |    return "" + l;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("1234567890") == result)
    }

    it("converts a Long to a String") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val s: String = JLong::toString(1234567890L);
          |    return s;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("1234567890") == result)
    }
  }

  describe("JDouble") {
    it("parses a string into a Double") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val d: Double = JDouble::parseDouble("3.14");
          |    return "" + d;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("3.14") == result)
    }

    it("converts a Double to a String") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val s: String = JDouble::toString(3.14);
          |    return s;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("3.14") == result)
    }
  }

  describe("JBoolean") {
    it("parses a string into a Boolean") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val b: Boolean = JBoolean::parseBoolean("true");
          |    return "" + b;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("true") == result)
    }

    it("converts a Boolean to a String") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val s: String = JBoolean::toString(true);
          |    return s;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("true") == result)
    }
  }
}
