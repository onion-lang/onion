package onion.compiler.tools

import onion.tools.Shell

/**
 * `Http.Response` reads its data through accessor methods only (#2738), like
 * `Proc.Result` and every record. Onion's parentheses-optional call keeps
 * `r.status` working: with no field of that name, it calls `status()`.
 */
class HttpResponseOneSpellingSpec extends AbstractShellSpec {

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

  describe("Http.Response") {
    it("has no public fields; status, body and headers are methods") {
      val fields = classOf[onion.Http.Response].getFields.map(_.getName).toSet
      assert(fields.isEmpty, s"public fields left: $fields")
      val methods = classOf[onion.Http.Response].getMethods.map(_.getName).toSet
      assert(Set("status", "body", "headers").subsetOf(methods))
    }

    it("reads the same value with and without parentheses") {
      val result = call(
        """    val r = new Http.Response(201, "hi", ["X-A: 1"])
          |    return r.status + "|" + r.status() + "|" + r.body + "|" + r.body() + "|" + r.headers.size + "|" + r.headers().size + "|#{r.status}"""".stripMargin)
      assert(Shell.Success("201|201|hi|hi|1|1|201") == result)
    }

    it("works in a lambda and in arithmetic") {
      val result = call(
        """    val f = (x: Http.Response) -> x.status * 2 + x.status()
          |    return "" + f(new Http.Response(404, "", []))""".stripMargin)
      assert(Shell.Success("1212") == result)
    }

    it("rejects assigning to status, as before") {
      val result = call(
        """    val r = new Http.Response(200, "", [])
          |    r.status = 500
          |    return "" + r.status""".stripMargin)
      assert(Shell.Failure(-1) == result)
    }
  }
}
