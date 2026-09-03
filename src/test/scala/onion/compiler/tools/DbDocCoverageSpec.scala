package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `Db` module docs.
 *
 * `onion.Db::connect` is overloaded: the 3-arg `connect(url, user, password)` and a
 * credential-less `connect(url)` that delegates to it with null user/password (for
 * databases needing no credentials, e.g. SQLite/H2). `onion.Db.Conn::isClosed` is also
 * a real, callable member alongside `close()`. docs/reference/stdlib.md and its Japanese
 * translation only ever showed the 3-arg `connect` call and `db.close()`, never the
 * single-arg `connect` overload or `isClosed()` -- even though `Concurrent`'s Channel
 * docs pair `close()`/`isClosed()` explicitly.
 */
class DbDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def hasSingleArgConnectCall(doc: String): Boolean =
    """Db::connect\([^,)]+\)""".r.findFirstIn(doc).isDefined

  private def hasIsClosedCall(doc: String): Boolean =
    doc.contains("db.isClosed()")

  it("actual onion.Db::connect has both a single-arg and a 3-arg overload (sanity check)") {
    val arities = classOf[onion.Db].getDeclaredMethods
      .filter(_.getName == "connect")
      .map(_.getParameterCount)
      .toSet
    assert(arities == Set(1, 3), s"expected connect/1 and connect/3 overloads, found arities: ${arities.toSeq.sorted}")
  }

  it("actual onion.Db.Conn exposes isClosed (sanity check)") {
    val hasIsClosed = classOf[onion.Db.Conn].getDeclaredMethods.exists(_.getName == "isClosed")
    assert(hasIsClosed, "reflection on onion.Db.Conn found no isClosed method -- the scan has rotted")
  }

  it("docs/reference/stdlib.md demonstrates the single-arg Db::connect(url) overload") {
    assert(hasSingleArgConnectCall(read("docs/reference/stdlib.md")),
      "docs/reference/stdlib.md never shows a single-arg Db::connect(url) call")
  }

  it("docs/ja/reference/stdlib.md demonstrates the single-arg Db::connect(url) overload") {
    assert(hasSingleArgConnectCall(read("docs/ja/reference/stdlib.md")),
      "docs/ja/reference/stdlib.md never shows a single-arg Db::connect(url) call")
  }

  it("docs/reference/stdlib.md documents Conn::isClosed()") {
    assert(hasIsClosedCall(read("docs/reference/stdlib.md")),
      "docs/reference/stdlib.md never shows db.isClosed()")
  }

  it("docs/ja/reference/stdlib.md documents Conn::isClosed()") {
    assert(hasIsClosedCall(read("docs/ja/reference/stdlib.md")),
      "docs/ja/reference/stdlib.md never shows db.isClosed()")
  }
}
