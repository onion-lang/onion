package onion.compiler.tools

import onion.tools.Shell

class RegexSpec extends AbstractShellSpec {
  describe("Regex library") {
    describe("matching") {
      it("matches entire string with pattern") {
        val result = shell.run(
          """
            |import { onion.Regex; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    if (Regex::matches("hello123", "[a-z]+[0-9]+")) {
            |      return "matched";
            |    } else {
            |      return "no match";
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("matched") == result)
      }

      it("fails when pattern does not match entire string") {
        val result = shell.run(
          """
            |import { onion.Regex; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    if (Regex::matches("hello123world", "[a-z]+[0-9]+")) {
            |      return "matched";
            |    } else {
            |      return "no match";
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("no match") == result)
      }

      it("finds pattern anywhere in string") {
        val result = shell.run(
          """
            |import { onion.Regex; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    if (Regex::find("abc123def", "[0-9]+")) {
            |      return "found";
            |    } else {
            |      return "not found";
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("found") == result)
      }
    }

    describe("extraction") {
      it("finds all matches") {
        val result = shell.run(
          """
            |import { onion.Regex; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val matches: String[] = Regex::findAll("a1b2c3", "[0-9]");
            |    return matches.length.toString();
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("3") == result)
      }

      it("finds first match") {
        val result = shell.run(
          """
            |import { onion.Regex; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return Regex::findFirst("hello world", "[a-z]+");
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("hello") == result)
      }

      it("returns empty string when no match found") {
        val result = shell.run(
          """
            |import { onion.Regex; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val found: String = Regex::findFirst("hello", "[0-9]+");
            |    if (found == "") {
            |      return "empty";
            |    } else {
            |      return found;
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("empty") == result)
      }

      it("extracts capturing groups") {
        val result = shell.run(
          """
            |import { onion.Regex; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val groups: String[] = Regex::groups("John:25", "([a-zA-Z]+):([0-9]+)");
            |    return groups[1] + "-" + groups[2];
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("John-25") == result)
      }
    }

    describe("replacement") {
      it("replaces all occurrences") {
        val result = shell.run(
          """
            |import { onion.Regex; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return Regex::replace("a1b2c3", "[0-9]", "X");
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("aXbXcX") == result)
      }

      it("replaces first occurrence only") {
        val result = shell.run(
          """
            |import { onion.Regex; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return Regex::replaceFirst("a1b2c3", "[0-9]", "X");
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("aXb2c3") == result)
      }

      it("supports backreferences in replacement") {
        val result = shell.run(
          """
            |import { onion.Regex; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return Regex::replace("hello world", "([a-z]+)", "[$1]");
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("[hello] [world]") == result)
      }
    }

    describe("splitting") {
      it("splits by pattern") {
        val result = shell.run(
          """
            |import { onion.Regex; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    // "a1b2c3" split by "[0-9]" gives ["a", "b", "c"] (trailing empty dropped)
            |    val parts: String[] = Regex::split("a1b2c3d", "[0-9]");
            |    return parts.length.toString();
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("4") == result)
      }

      it("splits by literal delimiter") {
        val result = shell.run(
          """
            |import { onion.Regex; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val parts: String[] = Regex::split("a-b-c-d", "-");
            |    return parts[0] + "," + parts[1] + "," + parts[2] + "," + parts[3];
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("a,b,c,d") == result)
      }
    }

    describe("utility") {
      it("quotes special characters") {
        val result = shell.run(
          """
            |import { onion.Regex; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val quoted: String = Regex::quote("[test]");
            |    if (Regex::find("[test]", quoted)) {
            |      return "found";
            |    } else {
            |      return "not found";
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("found") == result)
      }

      it("validates correct pattern") {
        val result = shell.run(
          """
            |import { onion.Regex; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    if (Regex::isValid("[a-z]+")) {
            |      return "valid";
            |    } else {
            |      return "invalid";
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("valid") == result)
      }

      it("detects invalid pattern") {
        val result = shell.run(
          """
            |import { onion.Regex; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    if (Regex::isValid("[invalid")) {
            |      return "valid";
            |    } else {
            |      return "invalid";
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("invalid") == result)
      }
    }
  }
}
