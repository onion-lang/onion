package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `IO` module docs.
 *
 * `onion.IO::input(prompt)` is a fully public stdlib method -- `readln(prompt)`
 * is itself implemented as `return input(prompt);`, so it's a real, independently
 * callable sibling of the documented `IO::readln(prompt)`, not a private helper.
 * docs/reference/stdlib.md and its Japanese translation document `IO::readln`
 * and `IO::readLine` but never mention `IO::input` at all.
 */
class IODocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def mentionsInput(doc: String): Boolean =
    "IO::input".r.findFirstIn(doc).isDefined

  it("actual onion.IO declares a public static input(String) method (sanity check)") {
    val method = classOf[onion.IO].getDeclaredMethod("input", classOf[String])
    assert(java.lang.reflect.Modifier.isStatic(method.getModifiers))
    assert(java.lang.reflect.Modifier.isPublic(method.getModifiers))
  }

  it("docs/reference/stdlib.md mentions IO::input") {
    assert(mentionsInput(read("docs/reference/stdlib.md")),
      "docs/reference/stdlib.md never mentions IO::input")
  }

  it("docs/ja/reference/stdlib.md mentions IO::input") {
    assert(mentionsInput(read("docs/ja/reference/stdlib.md")),
      "docs/ja/reference/stdlib.md never mentions IO::input")
  }
}
