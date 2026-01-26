package onion.compiler.tools

import onion.tools.Shell

class SmartCastSpec extends AbstractShellSpec {
  describe("Smart Cast") {
    describe("is operator") {
      it("narrows type after is check in then branch") {
        val result = shell.run(
          """
            |class Main {
            |public:
            |  static def main(args: String[]): String {
            |    val obj: Object = "hello";
            |    if (obj is String) {
            |      return obj.toUpperCase();
            |    }
            |    return "not string";
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("HELLO") == result)
      }

      it("does not narrow type in else branch for is check") {
        val result = shell.run(
          """
            |class Main {
            |public:
            |  static def main(args: String[]): String {
            |    val obj: Object = "hello";
            |    if (obj is String) {
            |      return obj.toUpperCase();
            |    } else {
            |      return obj.toString();
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("HELLO") == result)
      }
    }

    describe("null check") {
      it("narrows from T? to T after != null check") {
        val result = shell.run(
          """
            |class Main {
            |public:
            |  static def main(args: String[]): String {
            |    val s: String? = "hello";
            |    if (s != null) {
            |      return s.toUpperCase();
            |    }
            |    return "null";
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("HELLO") == result)
      }

      it("narrows in else branch after == null check") {
        val result = shell.run(
          """
            |class Main {
            |public:
            |  static def main(args: String[]): String {
            |    val s: String? = "hello";
            |    if (s == null) {
            |      return "null";
            |    } else {
            |      return s.toUpperCase();
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("HELLO") == result)
      }

      it("narrows with null on left side") {
        val result = shell.run(
          """
            |class Main {
            |public:
            |  static def main(args: String[]): String {
            |    val s: String? = "world";
            |    if (null != s) {
            |      return s.toLowerCase();
            |    }
            |    return "null";
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("world") == result)
      }
    }

    describe("mutable variable safety") {
      it("does not apply smart cast to mutable variables") {
        // var should still work but require explicit cast
        val result = shell.run(
          """
            |class Main {
            |public:
            |  static def main(args: String[]): String {
            |    var obj: Object = "hello";
            |    if (obj is String) {
            |      return (obj as String).toUpperCase();
            |    }
            |    return "not string";
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("HELLO") == result)
      }
    }

    describe("complex conditions") {
      it("narrows with && conditions") {
        val result = shell.run(
          """
            |class Main {
            |public:
            |  static def main(args: String[]): String {
            |    val obj: Object = "hello";
            |    if (obj is String && obj.length() > 0) {
            |      return obj.toUpperCase();
            |    }
            |    return "empty";
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("HELLO") == result)
      }

      it("narrows with multiple && conditions") {
        val result = shell.run(
          """
            |class Main {
            |public:
            |  static def main(args: String[]): String {
            |    val s: String? = "test";
            |    val n: Integer? = 42;
            |    if (s != null && n != null) {
            |      return s.toUpperCase() + n.toString();
            |    }
            |    return "null";
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("TEST42") == result)
      }
    }

    describe("nested conditions") {
      it("maintains narrowing in nested if") {
        val result = shell.run(
          """
            |class Main {
            |public:
            |  static def main(args: String[]): String {
            |    val obj: Object = "hello";
            |    if (obj is String) {
            |      if (obj.length() > 3) {
            |        return obj.toUpperCase();
            |      }
            |      return obj.toLowerCase();
            |    }
            |    return "not string";
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
