package onion.compiler.tools

import onion.tools.Shell

/**
 * Regex patterns in select: `case re"..." (g1, g2)` matches a String subject
 * against an ANCHORED regex literal and binds its capture groups. The literal
 * is validated at compile time: a malformed pattern is E0059 and a group
 * count / binding count mismatch is E0060.
 */
class RegexPatternSpec extends AbstractShellSpec {
  describe("re\"...\" patterns in select") {
    it("matches and binds capture groups") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def route(request: String): String {
          |    return select request {
          |      case re"GET (\S+) HTTP/(\S+)" (path, ver): "get " + path + " v" + ver
          |      case re"POST (\S+)" (path): "post " + path
          |      case re"PING": "pong"
          |      else: "bad"
          |    }
          |  }
          |  static def main(args: String[]): String {
          |    return route("GET /a HTTP/1.1") + ";" + route("POST /u") + ";" + route("PING") + ";" + route("zzz")
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("get /a v1.1;post /u;pong;bad") == result)
    }

    it("supports guards over the bound groups") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def classify(s: String): String {
          |    return select s {
          |      case re"(\d+)-(\d+)" (lo, hi) when Integer::parseInt(lo) < 20: "low " + lo + ".." + hi
          |      case re"(\d+)-(\d+)" (lo, hi): "high " + lo
          |      else: "other"
          |    }
          |  }
          |  static def main(args: String[]): String {
          |    return classify("12-34") + ";" + classify("99-1") + ";" + classify("xx")
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("low 12..34;high 99;other") == result)
    }

    it("is anchored: a substring match does not count") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return select "abc123def" {
          |      case re"\d+": "digits"
          |      else: "no"
          |    }
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("no") == result)
    }

    it("rejects a group/binding count mismatch at compile time (E0060)") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return select "x" {
          |      case re"(\d+)-(\d+)" (a): a
          |      else: "no"
          |    }
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }

    it("rejects a malformed regex literal at compile time (E0059)") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return select "x" {
          |      case re"([unclosed" (a): a
          |      else: "no"
          |    }
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }
  }
}
