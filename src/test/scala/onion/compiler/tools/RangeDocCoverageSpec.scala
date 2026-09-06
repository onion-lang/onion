package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for `onion.Range`, the runtime type behind the `a..b` / `a..<b` range
 * literals. Only the literal syntax itself was documented (specification.md); `start()`,
 * `endExclusive()`, `isEmpty()`, `size()` and `contains(value)` had no API-reference
 * coverage in either stdlib.md.
 */
class RangeDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private val members = Seq("start", "endExclusive", "isEmpty", "size", "contains", "toString")

  it("actual onion.Range exposes every documented-below member (sanity check)") {
    val names = classOf[onion.Range].getDeclaredMethods.map(_.getName).toSet
    members.foreach { name =>
      assert(names.contains(name), s"onion.Range lost method $name -- the scan has rotted")
    }
  }

  it("docs/reference/stdlib.md has a Range section mentioning every public member") {
    val doc = read("docs/reference/stdlib.md")
    assert(doc.contains("## Range"), "docs/reference/stdlib.md has no '## Range' section")
    members.foreach { name =>
      assert(doc.contains(name), s"docs/reference/stdlib.md doesn't mention $name, a public onion.Range member")
    }
  }

  it("docs/ja/reference/stdlib.md has a Range section mentioning every public member") {
    val doc = read("docs/ja/reference/stdlib.md")
    assert(doc.contains("## Range"), "docs/ja/reference/stdlib.md has no '## Range' section")
    members.foreach { name =>
      assert(doc.contains(name), s"docs/ja/reference/stdlib.md doesn't mention $name, a public onion.Range member")
    }
  }
}
