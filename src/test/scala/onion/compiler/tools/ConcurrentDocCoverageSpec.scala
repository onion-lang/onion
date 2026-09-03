package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `Concurrent` module docs.
 *
 * `onion.Concurrent` exposes a handful of static factories (`Concurrent::pool`,
 * `Concurrent::counter`, ...) plus instance methods on the `Pool`/`Counter`/`Lock`/`Channel`
 * types they return. docs/reference/stdlib.md and docs/ja/reference/stdlib.md only ever
 * showed a narrow "getting started" slice of each type in prose examples -- `cpus`,
 * `Counter.compareAndSet`, `Lock.acquire`/`release`/`tryAcquire`/`isHeld`,
 * `Channel.trySend`/`size`/`isEmpty`/`isClosed`/`drain` were all real, callable members with
 * no mention anywhere in either file.
 *
 * `Object` overrides (`toString`, `equals`, `hashCode`, ...) are excluded: they carry no
 * Concurrent-specific behavior worth documenting.
 */
class ConcurrentDocCoverageSpec extends AnyFunSpec {

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
    staticNames(classOf[onion.Concurrent]) ++
      instanceNames(classOf[onion.Concurrent.Pool]) ++
      instanceNames(classOf[onion.Concurrent.Counter]) ++
      instanceNames(classOf[onion.Concurrent.Lock]) ++
      instanceNames(classOf[onion.Concurrent.Channel])

  it("actual onion.Concurrent (and Pool/Counter/Lock/Channel) exposes the names this guard assumes (sanity check)") {
    assert(actualNames.nonEmpty, "reflection on onion.Concurrent found no members -- the scan has rotted")
  }

  it("docs/reference/stdlib.md documents every onion.Concurrent member") {
    val missing = actualNames -- documentedNames(read("docs/reference/stdlib.md"), actualNames)
    assert(missing.isEmpty, s"docs/reference/stdlib.md is missing Concurrent members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md documents every onion.Concurrent member") {
    val missing = actualNames -- documentedNames(read("docs/ja/reference/stdlib.md"), actualNames)
    assert(missing.isEmpty, s"docs/ja/reference/stdlib.md is missing Concurrent members: ${missing.toSeq.sorted.mkString(", ")}")
  }
}
