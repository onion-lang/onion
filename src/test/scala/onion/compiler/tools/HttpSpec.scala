package onion.compiler.tools

import onion.tools.Shell
import com.sun.net.httpserver.{HttpServer, HttpExchange, HttpHandler}
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

class HttpSpec extends AbstractShellSpec {

  private def withEchoServer()(test: (String, () => (String, String)) => Unit): Unit = {
    var seenMethod = ""
    var seenBody = ""
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = {
        seenMethod = exchange.getRequestMethod
        seenBody = new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
        val bytes = s"$seenMethod:$seenBody".getBytes(StandardCharsets.UTF_8)
        exchange.sendResponseHeaders(200, bytes.length)
        val os = exchange.getResponseBody
        try os.write(bytes) finally os.close()
      }
    })
    server.start()
    try {
      test(s"http://127.0.0.1:${server.getAddress.getPort}/", () => (seenMethod, seenBody))
    } finally server.stop(0)
  }

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
            |    val params: List[String] = ["name", "John", "age", "30"];
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
            |    val params: List[String] = [];
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
            |    val params: List[String] = ["q", "hello world"];
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
            |    val params: List[String] = ["id", "123"];
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
            |    val params: List[String] = ["extra", "value"];
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
            |    val params: List[String] = [];
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

    describe("PUT and DELETE requests") {
      it("sends a PUT request with a body") {
        withEchoServer() { (url, seen) =>
          val result = shell.run(
            s"""
               |import { onion.Http; }
               |class Test {
               |public:
               |  static def main(args: String[]): String {
               |    return Http::put("$url", "updated content");
               |  }
               |}
               |""".stripMargin,
            "None",
            Array()
          )
          assert(Shell.Success("PUT:updated content") == result)
          assert(("PUT", "updated content") == seen())
        }
      }

      it("sends a PUT request with a null body as empty") {
        withEchoServer() { (url, seen) =>
          val result = shell.run(
            s"""
               |import { onion.Http; }
               |class Test {
               |public:
               |  static def main(args: String[]): String {
               |    return Http::put("$url", null);
               |  }
               |}
               |""".stripMargin,
            "None",
            Array()
          )
          assert(Shell.Success("PUT:") == result)
          assert(("PUT", "") == seen())
        }
      }

      it("sends a DELETE request") {
        withEchoServer() { (url, seen) =>
          val result = shell.run(
            s"""
               |import { onion.Http; }
               |class Test {
               |public:
               |  static def main(args: String[]): String {
               |    return Http::delete("$url");
               |  }
               |}
               |""".stripMargin,
            "None",
            Array()
          )
          assert(Shell.Success("DELETE:") == result)
          assert(("DELETE", "") == seen())
        }
      }
    }

    describe("POST requests") {
      it("sends a POST request with a body") {
        withEchoServer() { (url, seen) =>
          val result = shell.run(
            s"""
               |import { onion.Http; }
               |class Test {
               |public:
               |  static def main(args: String[]): String {
               |    return Http::post("$url", "new content");
               |  }
               |}
               |""".stripMargin,
            "None",
            Array()
          )
          assert(Shell.Success("POST:new content") == result)
          assert(("POST", "new content") == seen())
        }
      }

      it("sends a POST request with a null body as empty") {
        withEchoServer() { (url, seen) =>
          val result = shell.run(
            s"""
               |import { onion.Http; }
               |class Test {
               |public:
               |  static def main(args: String[]): String {
               |    return Http::post("$url", null);
               |  }
               |}
               |""".stripMargin,
            "None",
            Array()
          )
          assert(Shell.Success("POST:") == result)
          assert(("POST", "") == seen())
        }
      }

      it("sends a POST request with a JSON body") {
        withEchoServer() { (url, seen) =>
          val result = shell.run(
            s"""
               |import { onion.Http; }
               |class Test {
               |public:
               |  static def main(args: String[]): String {
               |    return Http::postJson("$url", "{\\"a\\":1}");
               |  }
               |}
               |""".stripMargin,
            "None",
            Array()
          )
          assert(Shell.Success("POST:{\"a\":1}") == result)
          assert(("POST", "{\"a\":1}") == seen())
        }
      }

      it("sends a POST request with a null JSON body defaulting to an empty object") {
        withEchoServer() { (url, seen) =>
          val result = shell.run(
            s"""
               |import { onion.Http; }
               |class Test {
               |public:
               |  static def main(args: String[]): String {
               |    return Http::postJson("$url", null);
               |  }
               |}
               |""".stripMargin,
            "None",
            Array()
          )
          assert(Shell.Success("POST:{}") == result)
          assert(("POST", "{}") == seen())
        }
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
