package onion.compiler.tools

import onion.tools.Shell

class TypeAliasSpec extends AbstractShellSpec {
  describe("Type Aliases") {
    describe("simple type alias") {
      it("works with String alias") {
        val result = shell.run(
          """
            |type MyString = String;
            |class Main {
            |public:
            |  static def main(args: String[]): String {
            |    val s: MyString = "hello";
            |    return s.toUpperCase();
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("HELLO") == result)
      }

      it("works with ArrayList alias") {
        val result = shell.run(
          """
            |type StringList = ArrayList[String];
            |class Main {
            |public:
            |  static def main(args: String[]): String {
            |    val list: StringList = new ArrayList[String]();
            |    list.add("hello");
            |    return list.get(0);
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("hello") == result)
      }
    }

    describe("generic type alias") {
      it("works with generic alias with two type parameters") {
        val result = shell.run(
          """
            |type Pair[A, B] = HashMap[A, B];
            |class Main {
            |public:
            |  static def main(args: String[]): String {
            |    val p: Pair[String, String] = new HashMap[String, String]();
            |    p.put("key", "value");
            |    return p.get("key");
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("value") == result)
      }

      it("works with partially applied generic alias") {
        val result = shell.run(
          """
            |type StringMap[V] = HashMap[String, V];
            |class Main {
            |public:
            |  static def main(args: String[]): String {
            |    val m: StringMap[JInteger] = new HashMap[String, JInteger]();
            |    m.put("one", 1);
            |    return m.get("one").toString();
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("1") == result)
      }
    }

    describe("nullable type alias") {
      it("works with nullable type alias") {
        val result = shell.run(
          """
            |type MaybeString = String?;
            |class Main {
            |public:
            |  static def main(args: String[]): String {
            |    val s: MaybeString = null;
            |    if (s == null) {
            |      return "true";
            |    }
            |    return "false";
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("true") == result)
      }
    }

    describe("function type alias") {
      it("works with function type alias") {
        val result = shell.run(
          """
            |type Predicate = String -> Boolean;
            |class Main {
            |public:
            |  static def main(args: String[]): String {
            |    val isEmpty: Predicate = (s: String) -> { s.length() == 0 };
            |    if (isEmpty("")) {
            |      return "true";
            |    }
            |    return "false";
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("true") == result)
      }
    }

    describe("type alias in method parameters and return types") {
      it("works in method signature") {
        val result = shell.run(
          """
            |type Handler = String -> String;
            |class Main {
            |public:
            |  static def apply(h: Handler, s: String): String {
            |    return h(s);
            |  }
            |  static def main(args: String[]): String {
            |    val upper: Handler = (s: String) -> { s.toUpperCase() };
            |    return Main::apply(upper, "hello");
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
