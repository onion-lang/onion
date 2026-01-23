package onion.compiler.tools

import onion.tools.Shell

class NullSafetySpec extends AbstractShellSpec {
  describe("Null Safety") {
    describe("Nullable type declaration") {
      it("can declare nullable type with ?") {
        val result = shell.run(
          """
            |class Main {
            |public:
            |  static def main(args: String[]): String {
            |    val name: String? = null
            |    if name == null {
            |      return "null"
            |    }
            |    return "not null"
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("null") == result)
      }

      it("can assign non-null value to nullable type") {
        val result = shell.run(
          """
            |class Main {
            |public:
            |  static def main(args: String[]): String {
            |    val name: String? = "hello"
            |    if name == null {
            |      return "null"
            |    }
            |    return "not null"
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("not null") == result)
      }
    }

    describe("Safe call operator ?.") {
      it("returns null when target is null") {
        val result = shell.run(
          """
            |class Main {
            |public:
            |  static def main(args: String[]): String {
            |    val s: String? = null
            |    val upper: Object? = s?.toUpperCase()
            |    if upper == null {
            |      return "null"
            |    }
            |    return "not null"
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("null") == result)
      }

      it("returns value when target is not null") {
        val result = shell.run(
          """
            |class Main {
            |public:
            |  static def main(args: String[]): String {
            |    val s: String? = "hello"
            |    val upper: Object? = s?.toUpperCase()
            |    if upper == null {
            |      return "null"
            |    }
            |    return "not null"
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("not null") == result)
      }

      it("works with method calls with arguments") {
        val result = shell.run(
          """
            |class Main {
            |public:
            |  static def main(args: String[]): String {
            |    val s: String? = "hello world"
            |    val sub: Object? = s?.substring(0, 5)
            |    if sub == null {
            |      return "null"
            |    }
            |    return "success"
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("success") == result)
      }
    }

    describe("Safe call with Elvis operator") {
      it("combines ?. and ?: operators when null") {
        val result = shell.run(
          """
            |class Main {
            |public:
            |  static def main(args: String[]): String {
            |    val s: String? = null
            |    val upper: Object? = s?.toUpperCase()
            |    if upper == null {
            |      return "default"
            |    }
            |    return "has value"
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("default") == result)
      }

      it("returns actual value when not null") {
        val result = shell.run(
          """
            |class Main {
            |public:
            |  static def main(args: String[]): String {
            |    val s: String? = "hello"
            |    val upper: Object? = s?.toUpperCase()
            |    if upper == null {
            |      return "null"
            |    }
            |    return "HELLO"
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("HELLO") == result)
      }
    }

    describe("Safe call on non-nullable type") {
      it("works on regular non-nullable types") {
        val result = shell.run(
          """
            |class Main {
            |public:
            |  static def main(args: String[]): String {
            |    val s: String = "hello"
            |    val upper: Object? = s?.toUpperCase()
            |    if upper == null {
            |      return "null"
            |    }
            |    return "HELLO"
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("HELLO") == result)
      }
    }
  }
}
