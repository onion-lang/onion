package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `Option`/`Result` module docs.
 *
 * `onion.Option` and `onion.Result` are sealed interfaces whose instance methods are
 * declared directly on the interface (and implemented in the `Some`/`None`/`Ok`/`Err`
 * records), so reflection on the interface class itself finds the full member set.
 * docs/reference/stdlib.md and docs/ja/reference/stdlib.md documented only a subset of
 * each -- `isDefined`/`isEmpty`/`get`/`orElseThrow`/`forEach`/`bind` for Option and
 * `isOk`/`isErr`/`get`/`getError`/`getOrThrow`/`forEach`/`forEachError`/`bind` for
 * Result were all real, callable methods with no mention anywhere in either file.
 */
class OptionResultDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def documentedNames(doc: String, names: Set[String]): Set[String] =
    names.filter(n => s"\\b${java.util.regex.Pattern.quote(n)}\\b".r.findFirstIn(doc).isDefined)

  private def instanceMethodNames(c: Class[?]): Set[String] =
    c.getDeclaredMethods
      .filterNot(m => java.lang.reflect.Modifier.isStatic(m.getModifiers))
      .map(_.getName)
      .toSet

  private lazy val optionNames: Set[String] = instanceMethodNames(classOf[onion.Option[?]])
  private lazy val resultNames: Set[String] = instanceMethodNames(classOf[onion.Result[?, ?]])

  it("actual onion.Option exposes the names this guard assumes (sanity check)") {
    assert(optionNames.nonEmpty, "reflection on onion.Option found no instance members -- the scan has rotted")
  }

  it("actual onion.Result exposes the names this guard assumes (sanity check)") {
    assert(resultNames.nonEmpty, "reflection on onion.Result found no instance members -- the scan has rotted")
  }

  it("docs/reference/stdlib.md documents every onion.Option member") {
    val doc = read("docs/reference/stdlib.md")
    val missing = optionNames -- documentedNames(doc, optionNames)
    assert(missing.isEmpty, s"docs/reference/stdlib.md is missing Option members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md documents every onion.Option member") {
    val doc = read("docs/ja/reference/stdlib.md")
    val missing = optionNames -- documentedNames(doc, optionNames)
    assert(missing.isEmpty, s"docs/ja/reference/stdlib.md is missing Option members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/reference/stdlib.md documents every onion.Result member") {
    val doc = read("docs/reference/stdlib.md")
    val missing = resultNames -- documentedNames(doc, resultNames)
    assert(missing.isEmpty, s"docs/reference/stdlib.md is missing Result members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md documents every onion.Result member") {
    val doc = read("docs/ja/reference/stdlib.md")
    val missing = resultNames -- documentedNames(doc, resultNames)
    assert(missing.isEmpty, s"docs/ja/reference/stdlib.md is missing Result members: ${missing.toSeq.sorted.mkString(", ")}")
  }
}
