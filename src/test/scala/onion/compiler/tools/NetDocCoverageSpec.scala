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
 */
class NetDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def hasConnIsClosedCall(doc: String): Boolean =
    doc.contains("conn.isClosed()")

  private def hasListenerIsClosedCall(doc: String): Boolean =
    doc.contains("listener.isClosed()")

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
}
