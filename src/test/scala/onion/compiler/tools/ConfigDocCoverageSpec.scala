package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `Config` module docs.
 *
 * `onion.Config` is a genuine, default-imported stdlib module (`Config::loadJson`,
 * `Config::parseJson`, `Config::get`, `Config::getString`, `Config::getInt`,
 * `Config::getLong`, `Config::getDouble`, `Config::getBoolean`, `Config::getEnv`,
 * `Config::getWithEnvOverride`, `Config::hasPath`) with 11 distinct public static
 * member names, but -- unlike `OnionMath`, `Stats`, `Net`, `Proc`, `Scalars`,
 * `DateTime`, `Files`, `Rand`, `Csv`, `Hash`, `Codec`, `Text`, `Format`, `Assert`,
 * `Yaml`, `Archive` and `Server`, each already guarded by its own coverage spec --
 * it never had one. All 11 members were already documented in both files; this
 * guard now fails the build if a future addition to `onion.Config` goes
 * undocumented.
 */
class ConfigDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def documentedNames(doc: String): Set[String] =
    """Config::(\w+)""".r.findAllMatchIn(doc).map(_.group(1)).toSet

  private lazy val actualNames: Set[String] = {
    val c = classOf[onion.Config]
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

  it("actual onion.Config exposes the names this guard assumes (sanity check)") {
    assert(actualNames.nonEmpty, "reflection on onion.Config found no static members -- the scan has rotted")
  }

  it("docs/reference/stdlib.md documents every onion.Config member") {
    val documented = documentedNames(read("docs/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/reference/stdlib.md is missing Config:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md documents every onion.Config member") {
    val documented = documentedNames(read("docs/ja/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/ja/reference/stdlib.md is missing Config:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("\"Modules at a glance\" mentions Config in both languages") {
    assert(read("docs/reference/stdlib.md").contains("Config"),
      "docs/reference/stdlib.md's overview table should mention Config")
    assert(read("docs/ja/reference/stdlib.md").contains("Config"),
      "docs/ja/reference/stdlib.md's overview table should mention Config")
  }
}
