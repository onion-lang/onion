package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard tying `docs/reference/specification.md`'s "Auto-CLI" section (and its
 * Japanese translation) to the actual supported `main` parameter shapes in
 * `Rewriting.appendAutoCliCall`. Both pages only documented the "all scalar
 * parameters" shape (`def main(name: String, count: Int = 3): void`); they never
 * mentioned that a single `String[]` (raw argv) or a scalar prefix plus a trailing
 * `String[]` (rest collector, e.g. `def main(cmd: String, files: String[])`) are
 * also accepted, nor the placement restriction that rejects `String[]` anywhere
 * else (see `MainSignatureSpec`, `ArrayMainAutoCliSpec`).
 */
class AutoCliDocParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def autoCliSection(doc: String, nextHeading: String): String = {
    val start = doc.indexOf("### Auto-CLI")
    assert(start >= 0, "no \"### Auto-CLI\" heading found — the scan has rotted")
    val end = doc.indexOf(nextHeading, start)
    assert(end > start, s"no \"$nextHeading\" heading found after Auto-CLI — the scan has rotted")
    doc.substring(start, end)
  }

  private def check(path: String, nextHeading: String): Unit = {
    val section = autoCliSection(read(path), nextHeading)
    assert(section.contains("def main(args: String[])"),
      s"$path's Auto-CLI section doesn't document the single String[] (raw argv) shape")
    assert(section.contains("def main(cmd: String, files: String[])"),
      s"$path's Auto-CLI section doesn't document the scalar-prefix + String[] rest-collector shape")
    assert(section.contains("String[]") &&
      section.substring(section.indexOf("String[]") + 1).contains("String[]"),
      s"$path's Auto-CLI section doesn't mention the String[] placement restriction " +
      "(must be the only parameter, or the last one)")
  }

  it("docs/reference/specification.md documents the String[] auto-CLI shapes") {
    check("docs/reference/specification.md", "## Warnings")
  }

  it("docs/ja/reference/specification.md documents the String[] auto-CLI shapes") {
    check("docs/ja/reference/specification.md", "## 警告")
  }
}
