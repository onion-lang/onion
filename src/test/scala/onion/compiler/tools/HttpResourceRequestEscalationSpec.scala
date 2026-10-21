package onion.compiler.tools

import onion.tools.Shell
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.{ConcurrentLinkedQueue, Executors, TimeUnit}

/**
 * `http"...".request(method)` (#2146, RFC #2236) — the escalation path from the `http"..."`
 * resource literal to `Http::request`'s full builder, for a header, a timeout, a body on any
 * method, or the response status: everything `get`/`post`/`put`/`delete` don't cover.
 */
class HttpResourceRequestEscalationSpec extends AbstractShellSpec {

  private final case class Seen(method: String, path: String, body: String, headers: Map[String, Seq[String]])

  private def withServer()(test: (String, () => List[Seen]) => Unit): Unit = {
    val seen = new ConcurrentLinkedQueue[Seen]()
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
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
          case _          => (200, s"${ex.getRequestMethod}:$body")
        }
        val bytes = reply.getBytes(StandardCharsets.UTF_8)
        try {
          ex.sendResponseHeaders(status, if (bytes.isEmpty) -1 else bytes.length.toLong)
          if (bytes.nonEmpty) { val os = ex.getResponseBody; try os.write(bytes) finally os.close() }
        } finally ex.close()
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

  private def call(body: String): Shell.Result =
    shell.run(
      s"""class Test {
         |public:
         |  static def main(args: String[]): String {
         |$body
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  describe("""http"...".request(method) -> Http.Request""") {
    it("seeds the builder with the resource's own URL, and header/body/timeoutSeconds/send still work") {
      withServer() { (base, seen) =>
        val result = call(
          s"""    val r = http"$base/echo".request("POST").header("x-api-key", "k-123").body("hi").timeoutSeconds(5).send()
             |    val res = r.get()
             |    return r.isOk() + "|" + res.status + "|" + res.body""".stripMargin)
        assert(Shell.Success("true|200|POST:hi") == result)
        val s = seen().head
        assert(s.method == "POST")
        assert(s.headers.get("x-api-key").contains(Seq("k-123")))
      }
    }

    it("exposes the response status, which get()/post() alone cannot") {
      withServer() { (base, _) =>
        val result = call(
          s"""    val res = http"$base/missing".request("GET").send().get()
             |    return res.status + "|" + res.isError() + "|" + res.body""".stripMargin)
        assert(Shell.Success("404|true|no such thing") == result)
      }
    }

    it("is a fresh builder each call, independent of the other verb methods") {
      withServer() { (base, seen) =>
        val result = call(
          s"""    val resource = http"$base/x"
             |    val getBody = resource.get()
             |    val putRes = resource.request("PUT").body("v").send().get()
             |    return getBody + "|" + putRes.body""".stripMargin)
        assert(Shell.Success("GET:|PUT:v") == result)
        assert(seen().map(_.method) == List("GET", "PUT"))
      }
    }
  }
}
