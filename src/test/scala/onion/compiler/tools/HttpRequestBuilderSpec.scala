package onion.compiler.tools

import onion.tools.Shell
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.{ConcurrentLinkedQueue, Executors, TimeUnit}

/**
 * `Http::request(method, url)` — the request builder (FRICTION F10): any method, headers,
 * a body and a per-request timeout. `send()` returns `Result[Http.Response, Http.HttpFailure]`:
 * every HTTP response, 4xx/5xx included, is `Ok`; getting no response (timeout, refused
 * connection) is `Err` with a `kind`. Also `sendOrThrow()` and the header-taking
 * `getResponse`/`postResponse` overloads. Everything runs against a local `HttpServer` on
 * an ephemeral port, or a loopback port nothing listens on.
 */
class HttpRequestBuilderSpec extends AbstractShellSpec {

  /** What the server saw for one request. */
  private final case class Seen(method: String, path: String, body: String, headers: Map[String, Seq[String]])

  private def withServer()(test: (String, () => List[Seen]) => Unit): Unit = {
    val seen = new ConcurrentLinkedQueue[Seen]()
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    // A pool, so the deliberately slow handler does not block the dispatcher.
    val pool = Executors.newCachedThreadPool()
    server.setExecutor(pool)
    server.createContext("/", new HttpHandler {
      override def handle(ex: HttpExchange): Unit = {
        import scala.jdk.CollectionConverters._
        val body = new String(ex.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
        val headers = ex.getRequestHeaders.asScala.map { case (k, v) => k.toLowerCase -> v.asScala.toSeq }.toMap
        val path = ex.getRequestURI.getPath
        seen.add(Seen(ex.getRequestMethod, path, body, headers))
        val (status, reply) = path match {
          case "/missing" => (404, "no such thing")
          case "/boom"    => (500, "server broke")
          case "/slow"    => Thread.sleep(5000); (200, "too late")
          case _          => (200, s"${ex.getRequestMethod}:$body")
        }
        ex.getResponseHeaders.add("X-Reply", "yes")
        val bytes = reply.getBytes(StandardCharsets.UTF_8)
        try {
          ex.sendResponseHeaders(status, if (bytes.isEmpty) -1 else bytes.length.toLong)
          if (bytes.nonEmpty) { val os = ex.getResponseBody; try os.write(bytes) finally os.close() }
        } catch { case _: java.io.IOException => () } // the client gave up (timeout)
        finally ex.close()
      }
    })
    server.start()
    try {
      import scala.jdk.CollectionConverters._
      test(s"http://127.0.0.1:${server.getAddress.getPort}", () => seen.asScala.toList)
    } finally {
      server.stop(0)
      pool.shutdownNow()
      pool.awaitTermination(5, TimeUnit.SECONDS)
    }
  }

  /** Runs `body` as the body of a `static def main(...): String`. */
  private def call(body: String): Shell.Result =
    shell.run(
      s"""import { onion.Http; }
         |class Test {
         |public:
         |  static def main(args: String[]): String {
         |$body
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  describe("Http::request(...).send() -> Result[Http.Response, Http.HttpFailure]") {
    it("sends the headers it was given, and the body") {
      withServer() { (base, seen) =>
        val result = call(
          s"""    val r = Http::request("POST", "$base/echo").header("content-type", "application/json").header("x-api-key", "k-123").headers(["anthropic-version", "2023-06-01"]).body("{\\"a\\":1}").send()
             |    val res = r.get()
             |    return r.isOk() + "|" + res.status + "|" + res.body""".stripMargin)
        assert(Shell.Success("true|200|POST:{\"a\":1}") == result)
        val s = seen().head
        assert(s.method == "POST")
        assert(s.headers.get("content-type").contains(Seq("application/json")))
        assert(s.headers.get("x-api-key").contains(Seq("k-123")))
        assert(s.headers.get("anthropic-version").contains(Seq("2023-06-01")))
      }
    }

    it("chains across lines, the way a long request is written") {
      withServer() { (base, _) =>
        val result = call(
          s"""    val r = Http::request("POST", "$base/echo")
             |      .header("x-api-key", "k")
             |      .body("hi")
             |      .timeoutSeconds(30)
             |      .send()
             |    return r.get().body""".stripMargin)
        assert(Shell.Success("POST:hi") == result)
      }
    }

    it("returns a 4xx as Ok(response): the server answered") {
      withServer() { (base, _) =>
        val result = call(
          s"""    val r = Http::request("GET", "$base/missing").send()
             |    val res = r.get()
             |    return r.isOk() + "|" + res.status + "|" + res.isOk() + "|" + res.isError() + "|" + res.body""".stripMargin)
        assert(Shell.Success("true|404|false|true|no such thing") == result)
      }
    }

    it("returns a 5xx as Ok(response), and a 2xx as Ok") {
      withServer() { (base, _) =>
        val result = call(
          s"""    val bad = Http::request("POST", "$base/boom").body("x").send()
             |    val good = Http::request("GET", "$base/fine").send()
             |    return bad.isOk() + "|" + bad.get().status + "|" + bad.get().isError() + "|" + good.get().status + "|" + good.get().isOk()""".stripMargin)
        assert(Shell.Success("true|500|true|200|true") == result)
      }
    }

    it("composes with fold, map and do[Result]") {
      withServer() { (base, _) =>
        val result = call(
          s"""    val folded: String = Http::request("GET", "$base/missing").send().fold((f) -> "failed " + f.kind(), (res) -> "status " + res.status)
             |    val body = Http::request("PUT", "$base/x").body("v").send().map { res -> res.body }
             |    val st = do[Result] {
             |      a <- Http::request("GET", "$base/a").send()
             |      b <- Http::request("GET", "$base/missing").send()
             |      ret a.status + b.status
             |    }
             |    return folded + "|" + body.get() + "|" + st.get()""".stripMargin)
        assert(Shell.Success("status 404|PUT:v|604") == result)
      }
    }

    it("reads a response header case-insensitively") {
      withServer() { (base, _) =>
        val result = call(
          s"""    val res = Http::request("GET", "$base/fine").send().get()
             |    return res.header("x-reply") + "|" + res.header("X-REPLY") + "|" + res.header("absent")""".stripMargin)
        assert(Shell.Success("yes|yes|null") == result)
      }
    }

    it("returns Err(kind \"timeout\") when the timeout elapses, without throwing") {
      withServer() { (base, _) =>
        val started = System.nanoTime()
        val result = call(
          s"""    val r = Http::request("GET", "$base/slow").timeoutMillis(300L).send()
             |    if r.isOk() { return "no timeout" }
             |    val f = r.getError()
             |    return f.kind() + "|" + f.isTimeout() + "|" + f.isConnect() + "|" + f.method() + "|" + f.url() + "|" + (f.message() != null) + "|" + f.cause().getClass().getName()""".stripMargin)
        val elapsedMs = (System.nanoTime() - started) / 1000000
        assert(Shell.Success(s"timeout|true|false|GET|$base/slow|true|java.net.http.HttpTimeoutException") == result)
        assert(elapsedMs < 4500, s"the request waited ${elapsedMs}ms; the timeout did not fire")
      }
    }

    it("returns Err(kind \"connect\") when the connection is refused, and select can branch on kind") {
      val port = refusedPort()
      val result = call(
        s"""    val r = Http::request("GET", "http://127.0.0.1:$port/x").timeoutSeconds(5).send()
           |    if r.isOk() { return "connected" }
           |    val f = r.getError()
           |    val word: String = select f.kind() {
           |      case "timeout": "slow"
           |      case "connect": "unreachable"
           |      else: "broken"
           |    }
           |    return word + "|" + f.isConnect() + "|" + f.toString().startsWith("connect: GET http://127.0.0.1:$port/x: ")""".stripMargin)
      assert(Shell.Success("unreachable|true|true") == result)
    }

    it("sendOrThrow() returns the Response, and rethrows the original exception on no response") {
      withServer() { (base, _) =>
        val port = refusedPort()
        val result = call(
          s"""    val res = Http::request("GET", "$base/missing").sendOrThrow()
             |    var out = res.status + "|"
             |    try {
             |      Http::request("GET", "$base/slow").timeoutMillis(300L).sendOrThrow()
             |    } catch e: Exception { out = out + e.getClass().getName() + "|" }
             |    try {
             |      Http::request("GET", "http://127.0.0.1:$port/").sendOrThrow()
             |    } catch e: java.io.IOException { out = out + "io" }
             |    return out""".stripMargin)
        assert(Shell.Success("404|java.net.http.HttpTimeoutException|io") == result)
      }
    }

    it("sends PUT, DELETE and PATCH") {
      withServer() { (base, seen) =>
        val result = call(
          s"""    val put = Http::request("PUT", "$base/item").body("v2").send().get()
             |    val del = Http::request("DELETE", "$base/item").send().get()
             |    val patch = Http::request("PATCH", "$base/item").body("p").send().get()
             |    return put.body + "|" + del.body + "|" + patch.body""".stripMargin)
        assert(Shell.Success("PUT:v2|DELETE:|PATCH:p") == result)
        assert(seen().map(_.method) == List("PUT", "DELETE", "PATCH"))
      }
    }

    it("is immutable: a shared base request is extended, not changed") {
      withServer() { (base, seen) =>
        val result = call(
          s"""    val authed = Http::request("POST", "$base/echo").header("x-api-key", "k")
             |    val a = authed.header("x-extra", "1").body("a").send().get()
             |    val b = authed.body("b").send().get()
             |    return a.body + "|" + b.body + "|" + authed.toString()""".stripMargin)
        assert(Shell.Success(s"POST:a|POST:b|POST $base/echo") == result)
        val List(first, second) = seen(): @unchecked
        assert(first.headers.get("x-extra").contains(Seq("1")))
        assert(second.headers.get("x-extra").isEmpty)
        assert(second.headers.get("x-api-key").contains(Seq("k")))
      }
    }

    it("still throws IllegalArgumentException where a bad request is written: a programming error, not an outcome") {
      val result = call(
        """    var out = ""
          |    try { Http::request("GET", "not a url") } catch e: IllegalArgumentException { out = out + "url;" }
          |    try { Http::request("GET", "https://example.com").header("x-api-key", null) } catch e: IllegalArgumentException { out = out + "header;" }
          |    try { Http::request("GET", "https://example.com").headers(["odd"]) } catch e: IllegalArgumentException { out = out + "pairs;" }
          |    try { Http::request("GET", "https://example.com").timeoutSeconds(0) } catch e: IllegalArgumentException { out = out + "timeout;" }
          |    try { Http::request("GET", "https://example.com").header("Host", "x") } catch e: IllegalArgumentException { out = out + "restricted;" }
          |    return out""".stripMargin)
      assert(Shell.Success("url;header;pairs;timeout;restricted;") == result)
    }
  }

  describe("header-taking Response overloads") {
    it("getResponse(url, headers) and postResponse(url, body, headers) keep the status and send the headers") {
      withServer() { (base, seen) =>
        val result = call(
          s"""    val g = Http::getResponse("$base/missing", ["x-api-key", "g"])
             |    val p = Http::postResponse("$base/echo", "body", ["x-api-key", "p"])
             |    return g.status + "|" + p.status + "|" + p.body""".stripMargin)
        assert(Shell.Success("404|200|POST:body") == result)
        assert(seen().map(_.headers.get("x-api-key")) == List(Some(Seq("g")), Some(Seq("p"))))
      }
    }
  }

  /** A loopback port nothing listens on: bound, then closed. */
  private def refusedPort(): Int = {
    val s = new java.net.ServerSocket(0, 0, java.net.InetAddress.getLoopbackAddress)
    try s.getLocalPort finally s.close()
  }
}
