package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `Net` module docs.
 *
 * `onion.Net.Conn::isClosed` and `onion.Net.Listener::isClosed` are real, callable
 * members alongside each class's `close()`, but docs/reference/stdlib.md and its
 * Japanese translation only ever described `readLine`/`readAll`/`readBytes`/`write`/
 * `writeLine`/`writeBytes`/`timeout`/`closeWrite`/`close` for `Conn` and `accept`/`port`/
 * `close` for `Listener` -- never `isClosed()` on either, even though `Db.Conn` and
 * `Concurrent`'s Channel docs pair `close()`/`isClosed()` explicitly.
 *
 * `Net::listen(port)` -- the single-argument overload that binds every local address
 * with a default backlog of 50 -- is likewise real (`Net.java` even opens its own class
 * javadoc example with `Net::listen(0)`), but both doc files' `### Net::listen` section
 * only ever showed the three-argument `Net::listen(host, port, backlog)` form by name.
 */
class NetDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def hasConnIsClosedCall(doc: String): Boolean =
    doc.contains("conn.isClosed()")

  private def hasListenerIsClosedCall(doc: String): Boolean =
    doc.contains("listener.isClosed()")

  private def hasSingleArgListenCall(doc: String): Boolean =
    doc.contains("Net::listen(port)") || doc.contains("Net::listen(0)")

  it("actual onion.Net.Conn exposes isClosed (sanity check)") {
    val hasIsClosed = classOf[onion.Net.Conn].getDeclaredMethods.exists(_.getName == "isClosed")
    assert(hasIsClosed, "reflection on onion.Net.Conn found no isClosed method -- the scan has rotted")
  }

  it("actual onion.Net.Listener exposes isClosed (sanity check)") {
    val hasIsClosed = classOf[onion.Net.Listener].getDeclaredMethods.exists(_.getName == "isClosed")
    assert(hasIsClosed, "reflection on onion.Net.Listener found no isClosed method -- the scan has rotted")
  }

  it("docs/reference/stdlib.md documents Conn::isClosed()") {
    assert(hasConnIsClosedCall(read("docs/reference/stdlib.md")),
      "docs/reference/stdlib.md never shows conn.isClosed()")
  }

  it("docs/ja/reference/stdlib.md documents Conn::isClosed()") {
    assert(hasConnIsClosedCall(read("docs/ja/reference/stdlib.md")),
      "docs/ja/reference/stdlib.md never shows conn.isClosed()")
  }

  it("docs/reference/stdlib.md documents Listener::isClosed()") {
    assert(hasListenerIsClosedCall(read("docs/reference/stdlib.md")),
      "docs/reference/stdlib.md never shows listener.isClosed()")
  }

  it("docs/ja/reference/stdlib.md documents Listener::isClosed()") {
    assert(hasListenerIsClosedCall(read("docs/ja/reference/stdlib.md")),
      "docs/ja/reference/stdlib.md never shows listener.isClosed()")
  }

  it("actual onion.Net exposes the single-argument listen(port) overload (sanity check)") {
    val hasSingleArgListen = classOf[onion.Net].getDeclaredMethods.exists { m =>
      m.getName == "listen" && m.getParameterCount == 1
    }
    assert(hasSingleArgListen, "reflection on onion.Net found no one-argument listen method -- the scan has rotted")
  }

  it("docs/reference/stdlib.md documents the single-argument Net::listen(port) overload") {
    assert(hasSingleArgListenCall(read("docs/reference/stdlib.md")),
      "docs/reference/stdlib.md never shows Net::listen(port) or Net::listen(0)")
  }

  it("docs/ja/reference/stdlib.md documents the single-argument Net::listen(port) overload") {
    assert(hasSingleArgListenCall(read("docs/ja/reference/stdlib.md")),
      "docs/ja/reference/stdlib.md never shows Net::listen(port) or Net::listen(0)")
  }
}
