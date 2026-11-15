package onion.compiler.tools

import onion.tools.Shell

/**
 * #2772: a stdlib object that holds something to close (`Concurrent.Pool`, `Db.Conn`,
 * `Net.Conn`, `Net.Listener`, `Server.Instance`) works in `try (val r = ...) { }`.
 * Before, each was E0000 "type AutoCloseable is expected".
 */
class StdlibResourcesAutoCloseableSpec extends AbstractShellSpec {
  private def run(body: String): Shell.Result =
    shell.run("def main(args: String[]): String {\n" + body + "\n}", "None", Array())

  it("closes a Concurrent::pool at the end of try-with-resources") {
    assert(Shell.Success("42") == run(
      "  var out: Int = 0\n" +
        "  try (val pool = Concurrent::pool(2)) { out = pool.submit { 21 * 2 }.await() }\n" +
        "  return \"\" + out"))
  }

  it("closes a Net::listen listener at the end of try-with-resources") {
    assert(Shell.Success("true") == run(
      "  var port: Int = 0\n" +
        "  try (val l = Net::listen(0)) { port = l.port() }\n" +
        "  return \"\" + (port > 0)"))
  }

  it("closes a Server::start instance at the end of try-with-resources") {
    assert(Shell.Success("true") == run(
      "  var port: Int = 0\n" +
        "  try (val s = Server::start(0)) { port = s.port() }\n" +
        "  return \"\" + (port > 0)"))
  }

  it("accepts a Db connection and a Net connection as resources (compiles)") {
    val src =
      "import { onion.Db; onion.Net }\n" +
        "def a(): void { try (val c = Db::connect(\"jdbc:h2:mem:x\")) { println(c) } }\n" +
        "def b(port: Int): void { try (val c = Net::connect(\"127.0.0.1\", port)) { println(c) } }\n" +
        "def main(args: String[]): String { return \"ok\" }"
    assert(Shell.Success("ok") == shell.run(src, "None", Array()))
  }
}
