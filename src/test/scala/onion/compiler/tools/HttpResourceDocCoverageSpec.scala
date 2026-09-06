package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `http"…"` resource-literal docs.
 *
 * docs/reference/specification.md and its Japanese translation describe the
 * "fixed menu" that `file"…"` and `http"…"` expose as one shared list --
 * `text`, `lines`, `json`, `csv`, `csvRows` -- before introducing `read(shape)`.
 * That list is `FileResource`'s menu only: `HttpResource` (the runtime type
 * behind `http"…"`) has no `text`/`lines`/`csv`/`csvRows` methods at all --
 * its menu is the HTTP verbs `get`/`getJson`/`post`/`postJson`/`put`/`delete`.
 * The shared sentence therefore claims `http"…"` supports getters it does not
 * have, and omits the ones it does.
 */
class HttpResourceDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def methodNames(c: Class[_]): Set[String] =
    c.getDeclaredMethods.map(_.getName).toSet

  private def fixedMenuParagraph(doc: String): String = {
    val start = doc.indexOf("Reading a resource through a shape")
    val start2 = doc.indexOf("shape でリソースを読む")
    val from = if (start >= 0) start else start2
    assert(from >= 0, "doc has no 'reading a resource through a shape' section -- the scan has rotted")
    val fenceAfter = doc.indexOf("```onion", from)
    assert(fenceAfter >= 0, "no onion code fence found after the section heading -- the scan has rotted")
    doc.substring(from, fenceAfter)
  }

  it("actual onion.HttpResource has no text/lines/csv/csvRows methods (sanity check)") {
    val names = methodNames(classOf[onion.HttpResource])
    assert(!names.intersect(Set("text", "lines", "csv", "csvRows")).nonEmpty,
      s"onion.HttpResource unexpectedly gained a FileResource-style getter: $names -- the scan has rotted")
  }

  it("actual onion.HttpResource exposes the HTTP-verb menu (sanity check)") {
    val names = methodNames(classOf[onion.HttpResource])
    val verbs = Set("get", "getJson", "post", "postJson", "put", "delete")
    assert(verbs.subsetOf(names), s"onion.HttpResource is missing expected verb methods, found: $names")
  }

  it("docs/reference/specification.md's fixed-menu paragraph names http's actual verbs, not file's getters") {
    val para = fixedMenuParagraph(read("docs/reference/specification.md"))
    assert(para.contains("getJson") && para.contains("postJson") && para.contains("delete"),
      "docs/reference/specification.md describes http\"…\"'s fixed menu as file\"…\"'s " +
        "text/lines/csv/csvRows instead of http\"…\"'s own get/getJson/post/postJson/put/delete")
  }

  it("docs/ja/reference/specification.md's fixed-menu paragraph names http's actual verbs, not file's getters") {
    val para = fixedMenuParagraph(read("docs/ja/reference/specification.md"))
    assert(para.contains("getJson") && para.contains("postJson") && para.contains("delete"),
      "docs/ja/reference/specification.md describes http\"…\"'s fixed menu as file\"…\"'s " +
        "text/lines/csv/csvRows instead of http\"…\"'s own get/getJson/post/postJson/put/delete")
  }
}
