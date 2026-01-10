package onion.compiler.tools

import onion.tools.Shell

class HttpSpec extends AbstractShellSpec {
  describe("Http library") {
    describe("URL encoding/decoding") {
      it("encodes URL parameters") {
        val result = shell.run(
          """
            |import { onion.Http; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return Http::encodeUrl("hello world");
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("hello+world") == result)
      }

      it("decodes URL parameters") {
        val result = shell.run(
          """
            |import { onion.Http; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return Http::decodeUrl("hello+world");
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("hello world") == result)
      }

      it("encodes special characters") {
        val result = shell.run(
          """
            |import { onion.Http; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return Http::encodeUrl("a=b&c=d");
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("a%3Db%26c%3Dd") == result)
      }
    }

    describe("query string building") {
      it("builds query string from array") {
        val result = shell.run(
          """
            |import { onion.Http; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val params: String[] = new String[]{"name", "John", "age", "30"};
            |    return Http::buildQuery(params);
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("name=John&age=30") == result)
      }

      it("handles empty params") {
        val result = shell.run(
          """
            |import { onion.Http; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val params: String[] = new String[]{};
            |    return Http::buildQuery(params);
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("") == result)
      }

      it("encodes special characters in query values") {
        val result = shell.run(
          """
            |import { onion.Http; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val params: String[] = new String[]{"q", "hello world"};
            |    return Http::buildQuery(params);
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("q=hello+world") == result)
      }
    }

    describe("URL building") {
      it("builds URL with query parameters") {
        val result = shell.run(
          """
            |import { onion.Http; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val params: String[] = new String[]{"id", "123"};
            |    return Http::buildUrl("https://example.com/api", params);
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("https://example.com/api?id=123") == result)
      }

      it("appends to existing query string") {
        val result = shell.run(
          """
            |import { onion.Http; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val params: String[] = new String[]{"extra", "value"};
            |    return Http::buildUrl("https://example.com/api?existing=1", params);
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("https://example.com/api?existing=1&extra=value") == result)
      }

      it("returns base URL when no params") {
        val result = shell.run(
          """
            |import { onion.Http; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val params: String[] = new String[]{};
            |    return Http::buildUrl("https://example.com", params);
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("https://example.com") == result)
      }
    }

    describe("null handling") {
      it("handles null URL in encodeUrl") {
        val result = shell.run(
          """
            |import { onion.Http; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return Http::encodeUrl(null);
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("") == result)
      }

      it("handles null URL in decodeUrl") {
        val result = shell.run(
          """
            |import { onion.Http; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return Http::decodeUrl(null);
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("") == result)
      }
    }
  }
}
