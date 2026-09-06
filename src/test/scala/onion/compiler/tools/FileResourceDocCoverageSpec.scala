package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for `onion.FileResource`, the object behind the `file"…"` resource
 * literal. docs/reference/stdlib.md documents the static `onion.Files` module (`## Files
 * Module`) but never the instance returned by `file"…"` itself -- there is no `##
 * FileResource` section at all, so `path()`, `readLossless(shape)`, `eachLine(shape)`,
 * `exists()`, `write(content)` and `append(content)` are undiscoverable from the API
 * reference (only `text`/`lines`/`json`/`csv`/`csvRows`/`read(shape)` are mentioned, and
 * only in docs/reference/specification.md's syntax guide, not stdlib.md).
 */
class FileResourceDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private val members = Seq(
    "path", "text", "lines", "json", "csv", "csvRows",
    "read", "readLossless", "eachLine", "exists", "write", "append"
  )

  it("actual onion.FileResource exposes every documented-below member (sanity check)") {
    val names = classOf[onion.FileResource].getDeclaredMethods.map(_.getName).toSet
    members.foreach { name =>
      assert(names.contains(name), s"onion.FileResource lost method $name -- the scan has rotted")
    }
  }

  it("docs/reference/stdlib.md has a FileResource section mentioning every public member") {
    val doc = read("docs/reference/stdlib.md")
    assert(doc.contains("## FileResource"), "docs/reference/stdlib.md has no '## FileResource' section")
    members.foreach { name =>
      assert(doc.contains(name), s"docs/reference/stdlib.md doesn't mention $name, a public onion.FileResource member")
    }
  }

  it("docs/ja/reference/stdlib.md has a FileResource section mentioning every public member") {
    val doc = read("docs/ja/reference/stdlib.md")
    assert(doc.contains("## FileResource"), "docs/ja/reference/stdlib.md has no '## FileResource' section")
    members.foreach { name =>
      assert(doc.contains(name), s"docs/ja/reference/stdlib.md doesn't mention $name, a public onion.FileResource member")
    }
  }
}
