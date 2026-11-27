package onion.compiler.tools

import onion.tools.Shell

/** `Net.Conn.readAll()` after a read timeout keeps the bytes it had already read (#3015). */
class NetReadAllTimeoutSpec extends AbstractShellSpec {

  describe("Net.Conn.readAll") {
    it("keeps a partial line and earlier chunks across timeouts") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val l = Net::listen(0)
          |    val port = l.port()
          |    val t = new Thread(() -> {
          |      val s = l.accept()
          |      s.write("hel")
          |      Thread::sleep(500L)
          |      s.write("lo")
          |      Thread::sleep(500L)
          |      s.write(" world")
          |      s.close()
          |    })
          |    t.start()
          |    val c = Net::connect("127.0.0.1", port)
          |    c.timeout(200)
          |    val out = new ArrayList[String]
          |    try { out.add(c.readLine()) } catch e: RuntimeException { out.add("T1") }
          |    Thread::sleep(400L)
          |    try { out.add(c.readAll()) } catch e: RuntimeException { out.add("T2") }
          |    c.timeout(0)
          |    out.add("[" + c.readAll() + "]")
          |    c.close()
          |    l.close()
          |    t.join()
          |    return String::join(",", out)
          |  }
          |}
          |""".stripMargin,
        "NetReadAllTimeout.on",
        Array()
      )
      assert(Shell.Success("T1,T2,[hello world]") == result)
    }
  }
}
