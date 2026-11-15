package onion.compiler.tools

import onion.tools.Shell

/** `Net.Conn.readLine()` after a read timeout keeps the part of the line it had read (#2807). */
class NetReadLineTimeoutSpec extends AbstractShellSpec {

  private def run(body: String, name: String) = shell.run(
    s"""
       |class Test {
       |public:
       |  static def main(args: String[]): String {
       |    val l = Net::listen(0)
       |    val port = l.port()
       |    val t = new Thread(() -> {
       |      val s = l.accept()
       |$body
       |      s.close()
       |    })
       |    t.start()
       |    val c = Net::connect("127.0.0.1", port)
       |    c.timeout(200)
       |    val out = new ArrayList[String]
       |    foreach i in 1..3 {
       |      try { out.add(c.readLine()) } catch e: RuntimeException { out.add("TIMEOUT") }
       |      if i == 1 && out[0] == "TIMEOUT" { c.timeout(0) }
       |      if i == 2 { c.timeout(0) }
       |    }
       |    c.close()
       |    l.close()
       |    t.join()
       |    return String::join(",", out)
       |  }
       |}
       |""".stripMargin,
    name,
    Array()
  )

  describe("Net.Conn.readLine") {
    it("returns the whole line after a timeout in its middle") {
      val result = run(
        """      s.write("hel")
          |      Thread::sleep(600L)
          |      s.write("lo\r\nnext\n")""".stripMargin,
        "NetTimeoutMidLine.on")
      assert(Shell.Success("TIMEOUT,hello,next") == result)
    }

    it("a CR LF split by a timeout is still one terminator") {
      val result = run(
        """      s.write("one\r")
          |      Thread::sleep(600L)
          |      s.write("\ntwo\n")""".stripMargin,
        "NetTimeoutBeforeLf.on")
      assert(Shell.Success("one,TIMEOUT,two") == result)
    }
  }
}
