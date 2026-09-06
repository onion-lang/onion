package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `Http` module docs.
 *
 * `onion.Http` is a genuine, default-imported stdlib module (`Http::get`, `Http::post`,
 * `Http::postJson`, `Http::getResponse`, `Http::postResponse`, `Http::put`, `Http::delete`,
 * `Http::encodeUrl`, `Http::decodeUrl`, `Http::buildQuery`, `Http::buildUrl`) with 11 distinct
 * public static member names, but -- unlike `HttpResource` (the `http"..."` resource literal,
 * guarded by `HttpResourceDocCoverageSpec`), `Hash`, `OnionMath`, `Stats`, `Net`, `Proc`,
 * `Scalars`, `DateTime`, `Files`, `Rand` and `Csv`, each guarded by its own `*DocCoverageSpec`
 * -- it has never had a regression test checking that every member is still documented in both
 * docs/reference/stdlib.md and docs/ja/reference/stdlib.md. Every public member is checked here
 * so a future addition to onion.Http fails the build instead of silently staying undocumented.
 */
class HttpDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def documentedNames(doc: String): Set[String] =
    """Http::(\w+)""".r.findAllMatchIn(doc).map(_.group(1)).toSet

  private lazy val actualNames: Set[String] = {
    val c = classOf[onion.Http]
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

  it("actual onion.Http exposes the names this guard assumes (sanity check)") {
    assert(actualNames.nonEmpty, "reflection on onion.Http found no static members -- the scan has rotted")
  }

  it("docs/reference/stdlib.md documents every onion.Http member") {
    val documented = documentedNames(read("docs/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/reference/stdlib.md is missing Http:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md documents every onion.Http member") {
    val documented = documentedNames(read("docs/ja/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/ja/reference/stdlib.md is missing Http:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("\"Modules at a glance\" mentions Http in both languages") {
    assert(read("docs/reference/stdlib.md").contains("Http"),
      "docs/reference/stdlib.md's overview table should mention Http")
    assert(read("docs/ja/reference/stdlib.md").contains("Http"),
      "docs/ja/reference/stdlib.md's overview table should mention Http")
  }
}
