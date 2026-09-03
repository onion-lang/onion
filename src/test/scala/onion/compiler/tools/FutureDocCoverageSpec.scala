package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `Future` module docs.
 *
 * `onion.Future` pairs `underlying()` (Future -> Java `CompletableFuture`, documented
 * under "Conversions") with `Future::fromCompletableFuture` (Java `CompletableFuture` ->
 * Future) for the reverse direction, but only the former ever appeared in
 * docs/reference/stdlib.md or docs/ja/reference/stdlib.md.
 *
 * `Object` overrides (`toString`, `equals`, `hashCode`, ...) are excluded: they carry no
 * Future-specific behavior worth documenting.
 */
class FutureDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def documentedNames(doc: String, names: Set[String]): Set[String] =
    names.filter(n => s"\\b${java.util.regex.Pattern.quote(n)}\\b".r.findFirstIn(doc).isDefined)

  private val objectOverrides = Set("toString", "equals", "hashCode", "wait", "notify", "notifyAll", "getClass")

  private def staticNames(c: Class[?]): Set[String] =
    c.getDeclaredMethods
      .filter(m => java.lang.reflect.Modifier.isStatic(m.getModifiers))
      .filter(m => java.lang.reflect.Modifier.isPublic(m.getModifiers))
      .map(_.getName)
      .toSet

  private def instanceNames(c: Class[?]): Set[String] =
    c.getDeclaredMethods
      .filterNot(m => java.lang.reflect.Modifier.isStatic(m.getModifiers))
      .filter(m => java.lang.reflect.Modifier.isPublic(m.getModifiers))
      .map(_.getName)
      .filterNot(objectOverrides.contains)
      .toSet

  private lazy val actualNames: Set[String] =
    staticNames(classOf[onion.Future[?]]) ++ instanceNames(classOf[onion.Future[?]])

  it("actual onion.Future exposes the names this guard assumes (sanity check)") {
    assert(actualNames.nonEmpty, "reflection on onion.Future found no members -- the scan has rotted")
  }

  it("docs/reference/stdlib.md documents every onion.Future member") {
    val missing = actualNames -- documentedNames(read("docs/reference/stdlib.md"), actualNames)
    assert(missing.isEmpty, s"docs/reference/stdlib.md is missing Future members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md documents every onion.Future member") {
    val missing = actualNames -- documentedNames(read("docs/ja/reference/stdlib.md"), actualNames)
    assert(missing.isEmpty, s"docs/ja/reference/stdlib.md is missing Future members: ${missing.toSeq.sorted.mkString(", ")}")
  }
}
