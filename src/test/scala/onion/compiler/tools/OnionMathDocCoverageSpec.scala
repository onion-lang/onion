package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `OnionMath` module docs.
 *
 * `onion.OnionMath` is a genuine, default-imported stdlib module (`OnionMath::sin`,
 * `OnionMath::clamp`, `OnionMath::hypot`, ...) with its own effect tracking
 * (docs/reference/effects.md lists it as effectful), but it has never had a section in
 * docs/reference/stdlib.md or docs/ja/reference/stdlib.md — every public member is
 * checked here against both files so a future addition to onion.OnionMath fails the
 * build instead of silently staying undocumented.
 */
class OnionMathDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def documentedNames(doc: String): Set[String] =
    """OnionMath::(\w+)""".r.findAllMatchIn(doc).map(_.group(1)).toSet

  private lazy val actualNames: Set[String] = {
    val c = classOf[onion.OnionMath]
    val methodNames = c.getMethods
      .filter(m => java.lang.reflect.Modifier.isStatic(m.getModifiers))
      .filter(_.getDeclaringClass == c)
      .map(_.getName)
    val fieldNames = c.getFields
      .filter(f => java.lang.reflect.Modifier.isStatic(f.getModifiers))
      .filter(_.getDeclaringClass == c)
      .map(_.getName)
    (methodNames ++ fieldNames).toSet
  }

  it("actual onion.OnionMath exposes the names this guard assumes (sanity check)") {
    assert(actualNames.nonEmpty, "reflection on onion.OnionMath found no static members — the scan has rotted")
  }

  it("docs/reference/stdlib.md documents every onion.OnionMath member") {
    val documented = documentedNames(read("docs/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/reference/stdlib.md is missing OnionMath:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md documents every onion.OnionMath member") {
    val documented = documentedNames(read("docs/ja/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/ja/reference/stdlib.md is missing OnionMath:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("\"Modules at a glance\" mentions OnionMath in both languages") {
    assert(read("docs/reference/stdlib.md").contains("OnionMath"),
      "docs/reference/stdlib.md's overview table should mention OnionMath")
    assert(read("docs/ja/reference/stdlib.md").contains("OnionMath"),
      "docs/ja/reference/stdlib.md's overview table should mention OnionMath")
  }
}
