package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for `onion.HttpResource`, the object behind the `http"…"` resource
 * literal, in `docs/reference/stdlib.md`. That file documents the static `onion.Http`
 * module (`## Http`) but never the instance returned by `http"…"` itself -- there is no
 * `## HttpResource` section at all, so `url()`, `get(headers)`, `getJson()`,
 * `read(shape)`, `eachLine(shape)`, `post(body)`, `postJson(jsonBody)`, `put(body)` and
 * `delete()` are undiscoverable from the stdlib API reference, unlike the sibling
 * `file"…"` literal's `## FileResource` section. (`HttpResourceDocCoverageSpec` guards a
 * separate, narrower claim in `docs/reference/specification.md`'s fixed-menu paragraph;
 * this spec is about stdlib.md having no section for `HttpResource` at all.)
 */
class HttpResourceStdlibDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private val members = Seq(
    "url", "get", "getJson", "read", "eachLine", "post", "postJson", "put", "delete"
  )

  it("actual onion.HttpResource exposes every documented-below member (sanity check)") {
    val names = classOf[onion.HttpResource].getDeclaredMethods.map(_.getName).toSet
    members.foreach { name =>
      assert(names.contains(name), s"onion.HttpResource lost method $name -- the scan has rotted")
    }
  }

  it("docs/reference/stdlib.md has a HttpResource section mentioning every public member") {
    val doc = read("docs/reference/stdlib.md")
    assert(doc.contains("## HttpResource"), "docs/reference/stdlib.md has no '## HttpResource' section")
    members.foreach { name =>
      assert(doc.contains(name), s"docs/reference/stdlib.md doesn't mention $name, a public onion.HttpResource member")
    }
  }

  it("docs/ja/reference/stdlib.md has a HttpResource section mentioning every public member") {
    val doc = read("docs/ja/reference/stdlib.md")
    assert(doc.contains("## HttpResource"), "docs/ja/reference/stdlib.md has no '## HttpResource' section")
    members.foreach { name =>
      assert(doc.contains(name), s"docs/ja/reference/stdlib.md doesn't mention $name, a public onion.HttpResource member")
    }
  }
}
