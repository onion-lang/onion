package onion.compiler.tools

import onion.tools.Shell

/** Bytes a timed-out `readAll` kept still split into lines for a later `readLine` (#3236). */
class NetReadAllThenLinesSpec extends AbstractShellSpec {

  describe("Net.Conn.readLine after a timed-out readAll") {
    it("keeps the line breaks of the saved bytes") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val l = Net::listen(0)
          |    val port = l.port()
          |    val t = new Thread(() -> {
          |      val s = l.accept()
          |      s.write("a\nb")
          |      Thread::sleep(600L)
          |      s.write("c\nd")
          |      s.close()
          |    })
          |    t.start()
          |    val c = Net::connect("127.0.0.1", port)
          |    c.timeout(200)
          |    val out = new ArrayList[String]
          |    try { out.add(c.readAll()) } catch e: RuntimeException { out.add("TIMEOUT") }
          |    c.timeout(0)
          |    out.add(c.readLine())
          |    out.add(c.readLine())
          |    out.add(c.readLine())
          |    c.close()
          |    l.close()
          |    t.join()
          |    return String::join(",", out)
          |  }
          |}
          |""".stripMargin,
        "NetReadAllThenLines.on",
        Array()
      )
      assert(Shell.Success("TIMEOUT,a,bc,d") == result)
    }
  }
}
