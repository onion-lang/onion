package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `Proc` module docs.
 *
 * `onion.Proc` exposes `capture`/`captureIn`/`run`/`runIn`/`exec`/`execIn` plus instance
 * methods on the `Result` type `capture`/`captureIn` return. docs/reference/stdlib.md and
 * docs/ja/reference/stdlib.md showed `capture`/`run`/`exec`/`captureIn` and, on `Result`,
 * only `status`/`stdout`/`stderr`/`succeeded` -- `runIn`, `execIn`, and `Result.failed()`
 * were all real, callable members with no mention anywhere in either file, even though the
 * doc text says "...In variants set the working directory" (plural) without ever showing
 * `runIn`/`execIn` themselves.
 *
 * `Object` overrides (`toString`, `equals`, `hashCode`, ...) are excluded: they carry no
 * Proc-specific behavior worth documenting.
 */
class ProcDocCoverageSpec extends AnyFunSpec {

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
    staticNames(classOf[onion.Proc]) ++ instanceNames(classOf[onion.Proc.Result])

  it("actual onion.Proc (and Result) exposes the names this guard assumes (sanity check)") {
    assert(actualNames.nonEmpty, "reflection on onion.Proc found no members -- the scan has rotted")
  }

  it("docs/reference/stdlib.md documents every onion.Proc member") {
    val missing = actualNames -- documentedNames(read("docs/reference/stdlib.md"), actualNames)
    assert(missing.isEmpty, s"docs/reference/stdlib.md is missing Proc members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md documents every onion.Proc member") {
    val missing = actualNames -- documentedNames(read("docs/ja/reference/stdlib.md"), actualNames)
    assert(missing.isEmpty, s"docs/ja/reference/stdlib.md is missing Proc members: ${missing.toSeq.sorted.mkString(", ")}")
  }
}
