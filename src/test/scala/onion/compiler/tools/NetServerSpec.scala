package onion.compiler.tools

import onion.tools.Shell

/**
 * `onion.Net` and `onion.Server`, tested against each other.
 *
 * Onion could reach the network only as an HTTP client: there was no way to speak any
 * other protocol and no way to accept a connection at all. These two close that, and the
 * cheapest honest test of either is the other — a server answering a request that a raw
 * socket sent proves the whole path, including that an Onion lambda converts to
 * `Server.Handler` and that a handler's response actually reaches a client.
 *
 * Every server binds `localhost` on port 0: the OS picks a free port, `port()` reports it,
 * and nothing here is reachable from the rest of the network or collides with a developer's
 * running service.
 */
class NetServerSpec extends AbstractShellSpec {

  describe("Server") {
    it("answers a request sent over a raw socket") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val server = Server::start("localhost", 0)
          |    server.handle("/hello", (req) -> Server::text("hi " + req.method()))
          |    val conn = Net::connect("localhost", server.port())
          |    conn.writeLine("GET /hello HTTP/1.0")
          |    conn.writeLine("Host: localhost")
          |    conn.writeLine("")
          |    val text = conn.readAll()
          |    conn.close()
          |    server.stop()
          |    if text.contains("200") && text.contains("hi GET") { return "ok" }
          |    return text
          |  }
          |}
          |""".stripMargin,
        "ServerRoundTrip.on",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("reports the port the OS actually chose, which is what makes it testable") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val server = Server::start("localhost", 0)
          |    val port = server.port()
          |    server.stop()
          |    if port > 0 && port <= 65535 { return "bound" }
          |    return "port was " + port
          |  }
          |}
          |""".stripMargin,
        "ServerEphemeralPort.on",
        Array()
      )
      assert(Shell.Success("bound") == result)
    }

    it("falls back to handleAll, which is where select over the path belongs") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val server = Server::start("localhost", 0)
          |    server.handleAll((req) -> select req.path() {
          |      case "/health": Server::text("ok")
          |      else: Server::notFound()
          |    })
          |    val conn = Net::connect("localhost", server.port())
          |    conn.writeLine("GET /health HTTP/1.0")
          |    conn.writeLine("")
          |    val healthy = conn.readAll()
          |    conn.close()
          |
          |    val other = Net::connect("localhost", server.port())
          |    other.writeLine("GET /nope HTTP/1.0")
          |    other.writeLine("")
          |    val missing = other.readAll()
          |    other.close()
          |    server.stop()
          |
          |    if healthy.contains("ok") && missing.contains("404") { return "routed" }
          |    return healthy + "|" + missing
          |  }
          |}
          |""".stripMargin,
        "ServerHandleAll.on",
        Array()
      )
      assert(Shell.Success("routed") == result)
    }

    it("answers 500 instead of hanging when a handler throws") {
      // A handler that blows up must not take the server down or leave the client waiting
      // on a socket that never answers, which is the failure mode worth testing.
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val server = Server::start("localhost", 0)
          |    server.handle("/boom", (req) -> throw new RuntimeException("deliberate"))
          |    val conn = Net::connect("localhost", server.port())
          |    conn.writeLine("GET /boom HTTP/1.0")
          |    conn.writeLine("")
          |    val text = conn.readAll()
          |    conn.close()
          |    server.stop()
          |    if text.contains("500") { return "handled" }
          |    return text
          |  }
          |}
          |""".stripMargin,
        "ServerHandlerThrows.on",
        Array()
      )
      assert(Shell.Success("handled") == result)
    }

    it("builds a response value without any socket, so a handler is testable alone") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val r = Server::json("{\"a\":1}").withStatus(201).withHeader("X-Test", "yes")
          |    return r.status() + ":" + r.body()
          |  }
          |}
          |""".stripMargin,
        "ServerResponseValue.on",
        Array()
      )
      assert(Shell.Success("201:{\"a\":1}") == result)
    }
  }

  describe("Net") {
    it("accepts a connection and talks to it") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val listener = Net::listen("localhost", 0, 4)
          |    val port = listener.port()
          |
          |    val server = Future::async(() -> {
          |      val peer = listener.accept()
          |      val line = peer.readLine()
          |      peer.writeLine("echo: " + line)
          |      peer.close()
          |      return "served"
          |    })
          |
          |    val client = Net::connect("localhost", port)
          |    client.writeLine("ping")
          |    val reply = client.readLine()
          |    client.close()
          |    server.await()
          |    listener.close()
          |    return reply
          |  }
          |}
          |""".stripMargin,
        "NetEcho.on",
        Array()
      )
      assert(Shell.Success("echo: ping") == result)
    }

    it("names the address in the failure when a connection is refused") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    // Bind and immediately release a port, so nothing is listening on it.
          |    val listener = Net::listen("localhost", 0, 1)
          |    val port = listener.port()
          |    listener.close()
          |    try {
          |      Net::connect("localhost", port, 500)
          |      return "unexpectedly connected"
          |    } catch e: Exception {
          |      if e.getMessage().contains("" + port) { return "named" }
          |      return e.getMessage()
          |    }
          |  }
          |}
          |""".stripMargin,
        "NetRefused.on",
        Array()
      )
      assert(Shell.Success("named") == result)
    }

    it("rejects a port outside the valid range before touching the network") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    try {
          |      Net::listen("localhost", 70000, 1)
          |      return "unexpectedly bound"
          |    } catch e: Exception {
          |      if e.getMessage().contains("70000") { return "rejected" }
          |      return e.getMessage()
          |    }
          |  }
          |}
          |""".stripMargin,
        "NetBadPort.on",
        Array()
      )
      assert(Shell.Success("rejected") == result)
    }
  }
}
