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
            |    val matches: List[String] = Regex::findAll("a1b2c3", "[0-9]");
            |    return matches.size.toString();
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
            |    val groups: List[String] = Regex::groups("John:25", "([a-zA-Z]+):([0-9]+)");
            |    return groups[1] + "-" + groups[2];
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("John-25") == result)
      }

      it("extracts capturing groups from every match") {
        val result = shell.run(
          """
            |import { onion.Regex; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val all: List[List[String]] = Regex::groupsAll("a1b2c3", "([a-z])([0-9])");
            |    var result: String = "";
            |    foreach g: List[String] in all {
            |      result = result + g[0] + "=" + g[1] + "+" + g[2] + ";";
            |    }
            |    return result;
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("a1=a+1;b2=b+2;c3=c+3;") == result)
      }

      it("returns an empty list when groupsAll finds no match") {
        val result = shell.run(
          """
            |import { onion.Regex; }
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val all: List[List[String]] = Regex::groupsAll("no digits here", "([0-9]+)");
            |    return all.size;
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success(0) == result)
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
            |    val parts: List[String] = Regex::split("a1b2c3d", "[0-9]");
            |    return parts.size.toString();
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
            |    val parts: List[String] = Regex::split("a-b-c-d", "-");
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

    describe("invalid pattern") {
      it("reports an invalid pattern to matches with an Regex::matches-prefixed message, not the raw JDK one") {
        val result = shell.run(
          """
            |import { onion.Regex; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    try {
            |      Regex::matches("abc", "[");
            |      return "no-throw";
            |    } catch e: IllegalArgumentException {
            |      return e.getMessage();
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        result match {
          case Shell.Success(message) => assert(message.asInstanceOf[String].startsWith("Regex::matches:"))
          case other => fail(s"expected Shell.Success, got $other")
        }
      }

      it("reports an invalid pattern to find with an Regex::find-prefixed message, not the raw JDK one") {
        val result = shell.run(
          """
            |import { onion.Regex; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    try {
            |      Regex::find("abc", "[");
            |      return "no-throw";
            |    } catch e: IllegalArgumentException {
            |      return e.getMessage();
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        result match {
          case Shell.Success(message) => assert(message.asInstanceOf[String].startsWith("Regex::find:"))
          case other => fail(s"expected Shell.Success, got $other")
        }
      }

      it("reports an invalid pattern to findAll with an Regex::findAll-prefixed message, not the raw JDK one") {
        val result = shell.run(
          """
            |import { onion.Regex; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    try {
            |      Regex::findAll("abc", "[");
            |      return "no-throw";
            |    } catch e: IllegalArgumentException {
            |      return e.getMessage();
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        result match {
          case Shell.Success(message) => assert(message.asInstanceOf[String].startsWith("Regex::findAll:"))
          case other => fail(s"expected Shell.Success, got $other")
        }
      }

      it("reports an invalid pattern to findFirst with an Regex::findFirst-prefixed message, not the raw JDK one") {
        val result = shell.run(
          """
            |import { onion.Regex; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    try {
            |      Regex::findFirst("abc", "[");
            |      return "no-throw";
            |    } catch e: IllegalArgumentException {
            |      return e.getMessage();
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        result match {
          case Shell.Success(message) => assert(message.asInstanceOf[String].startsWith("Regex::findFirst:"))
          case other => fail(s"expected Shell.Success, got $other")
        }
      }

      it("reports an invalid pattern to groups with an Regex::groups-prefixed message, not the raw JDK one") {
        val result = shell.run(
          """
            |import { onion.Regex; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    try {
            |      Regex::groups("abc", "[");
            |      return "no-throw";
            |    } catch e: IllegalArgumentException {
            |      return e.getMessage();
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        result match {
          case Shell.Success(message) => assert(message.asInstanceOf[String].startsWith("Regex::groups:"))
          case other => fail(s"expected Shell.Success, got $other")
        }
      }

      it("reports an invalid pattern to groupsAll with an Regex::groupsAll-prefixed message, not the raw JDK one") {
        val result = shell.run(
          """
            |import { onion.Regex; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    try {
            |      Regex::groupsAll("abc", "[");
            |      return "no-throw";
            |    } catch e: IllegalArgumentException {
            |      return e.getMessage();
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        result match {
          case Shell.Success(message) => assert(message.asInstanceOf[String].startsWith("Regex::groupsAll:"))
          case other => fail(s"expected Shell.Success, got $other")
        }
      }

      it("reports an invalid pattern to replace with an Regex::replace-prefixed message, not the raw JDK one") {
        val result = shell.run(
          """
            |import { onion.Regex; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    try {
            |      Regex::replace("abc", "[", "x");
            |      return "no-throw";
            |    } catch e: IllegalArgumentException {
            |      return e.getMessage();
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        result match {
          case Shell.Success(message) => assert(message.asInstanceOf[String].startsWith("Regex::replace:"))
          case other => fail(s"expected Shell.Success, got $other")
        }
      }

      it("reports an invalid pattern to replaceFirst with an Regex::replaceFirst-prefixed message, not the raw JDK one") {
        val result = shell.run(
          """
            |import { onion.Regex; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    try {
            |      Regex::replaceFirst("abc", "[", "x");
            |      return "no-throw";
            |    } catch e: IllegalArgumentException {
            |      return e.getMessage();
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        result match {
          case Shell.Success(message) => assert(message.asInstanceOf[String].startsWith("Regex::replaceFirst:"))
          case other => fail(s"expected Shell.Success, got $other")
        }
      }

      it("reports an invalid pattern to split with an Regex::split-prefixed message, not the raw JDK one") {
        val result = shell.run(
          """
            |import { onion.Regex; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    try {
            |      Regex::split("abc", "[");
            |      return "no-throw";
            |    } catch e: IllegalArgumentException {
            |      return e.getMessage();
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        result match {
          case Shell.Success(message) => assert(message.asInstanceOf[String].startsWith("Regex::split:"))
          case other => fail(s"expected Shell.Success, got $other")
        }
      }

      it("reports an invalid pattern to split(limit) with an Regex::split-prefixed message, not the raw JDK one") {
        val result = shell.run(
          """
            |import { onion.Regex; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    try {
            |      Regex::split("abc", "[", 2);
            |      return "no-throw";
            |    } catch e: IllegalArgumentException {
            |      return e.getMessage();
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        result match {
          case Shell.Success(message) => assert(message.asInstanceOf[String].startsWith("Regex::split:"))
          case other => fail(s"expected Shell.Success, got $other")
        }
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
