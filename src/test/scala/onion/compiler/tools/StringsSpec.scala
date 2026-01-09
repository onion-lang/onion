package onion.compiler.tools

import onion.tools.Shell

class StringsSpec extends AbstractShellSpec {
  describe("Strings library") {
    describe("split and join") {
      it("splits string by delimiter") {
        val result = shell.run(
          """
            |import { onion.Strings; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val parts: String[] = Strings::split("a,b,c", ",");
            |    return parts.length.toString();
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("3") == result)
      }

      it("joins array with delimiter") {
        val result = shell.run(
          """
            |import { onion.Strings; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val parts: String[] = new String[]{"x", "y", "z"};
            |    return Strings::join(parts, "-");
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("x-y-z") == result)
      }
    }

    describe("transformation") {
      it("trims whitespace") {
        val result = shell.run(
          """
            |import { onion.Strings; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return Strings::trim("  hello  ");
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("hello") == result)
      }

      it("converts to upper case") {
        val result = shell.run(
          """
            |import { onion.Strings; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return Strings::upper("hello");
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("HELLO") == result)
      }

      it("converts to lower case") {
        val result = shell.run(
          """
            |import { onion.Strings; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return Strings::lower("HELLO");
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("hello") == result)
      }

      it("replaces substring") {
        val result = shell.run(
          """
            |import { onion.Strings; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return Strings::replace("hello world", "world", "onion");
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("hello onion") == result)
      }

      it("reverses string") {
        val result = shell.run(
          """
            |import { onion.Strings; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return Strings::reverse("abc");
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("cba") == result)
      }
    }

    describe("inspection") {
      it("checks startsWith") {
        val result = shell.run(
          """
            |import { onion.Strings; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    if (Strings::startsWith("hello", "he")) {
            |      return "yes";
            |    } else {
            |      return "no";
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("yes") == result)
      }

      it("checks endsWith") {
        val result = shell.run(
          """
            |import { onion.Strings; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    if (Strings::endsWith("hello", "lo")) {
            |      return "yes";
            |    } else {
            |      return "no";
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("yes") == result)
      }

      it("checks contains") {
        val result = shell.run(
          """
            |import { onion.Strings; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    if (Strings::contains("hello", "ell")) {
            |      return "yes";
            |    } else {
            |      return "no";
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("yes") == result)
      }

      it("checks isEmpty") {
        val result = shell.run(
          """
            |import { onion.Strings; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    if (Strings::isEmpty("")) {
            |      return "empty";
            |    } else {
            |      return "not empty";
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("empty") == result)
      }

      it("checks isBlank") {
        val result = shell.run(
          """
            |import { onion.Strings; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    if (Strings::isBlank("   ")) {
            |      return "blank";
            |    } else {
            |      return "not blank";
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("blank") == result)
      }
    }

    describe("extraction") {
      it("gets substring from start") {
        val result = shell.run(
          """
            |import { onion.Strings; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return Strings::substring("hello", 2);
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("llo") == result)
      }

      it("gets substring with range") {
        val result = shell.run(
          """
            |import { onion.Strings; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return Strings::substring("hello", 1, 4);
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("ell") == result)
      }

      it("finds indexOf") {
        val result = shell.run(
          """
            |import { onion.Strings; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return Strings::indexOf("hello", "l").toString();
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("2") == result)
      }

      it("finds lastIndexOf") {
        val result = shell.run(
          """
            |import { onion.Strings; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return Strings::lastIndexOf("hello", "l").toString();
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("3") == result)
      }
    }

    describe("padding") {
      it("pads left with character") {
        val result = shell.run(
          """
            |import { onion.Strings; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return Strings::padLeft("42", 5, '0');
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("00042") == result)
      }

      it("pads right with character") {
        val result = shell.run(
          """
            |import { onion.Strings; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return Strings::padRight("hi", 5, '.');
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("hi...") == result)
      }

      it("repeats string") {
        val result = shell.run(
          """
            |import { onion.Strings; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return Strings::repeat("ab", 3);
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("ababab") == result)
      }
    }
  }
}
