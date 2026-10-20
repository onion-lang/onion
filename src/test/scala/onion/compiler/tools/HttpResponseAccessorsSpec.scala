package onion.compiler.tools

import onion.tools.Shell

/**
 * `Http.Response` exposed `status`/`body`/`headers` only as fields, so `r.status()` -- the
 * spelling `Server.Response.status()`, `HttpFailure.kind()` and every record accessor use --
 * was E0005. Accessor methods of the same names now sit beside the fields; both spellings
 * work.
 */
class HttpResponseAccessorsSpec extends AbstractShellSpec {
  private def eval(body: String): Shell.Result =
    shell.run(
      s"""
         |class Test {
         |public:
         |  static def main(args: String[]): String {
         |    val r = new Http.Response(201, "made", ["Content-Type: text/plain"])
         |    $body
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  describe("Http.Response accessors") {
    it("status(), body() and headers() as methods") {
      assert(eval("""return "" + r.status() + r.body() + r.headers().size""") == Shell.Success("201made1"))
    }
    it("the fields still work") {
      assert(eval("""return "" + r.status + r.body""") == Shell.Success("201made"))
    }
  }
}
