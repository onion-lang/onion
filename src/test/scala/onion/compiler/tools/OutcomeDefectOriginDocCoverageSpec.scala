package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `Outcome`/`Defect`/`Origin` module docs.
 *
 * `onion.Outcome` is a sealed interface whose instance methods are declared directly on
 * the interface (mirrors OptionResultDocCoverageSpec), so reflection on the interface
 * class itself finds the full member set. `Defect` and `Origin` are records: reflecting
 * on a record class also returns its compiler-generated component accessors, `equals`,
 * `hashCode` and `toString`, which aren't independently-named API worth documenting by
 * name, so their explicitly-declared behavioural methods are checked by name instead.
 * docs/reference/stdlib.md and docs/ja/reference/stdlib.md documented most of the
 * surface but missed `Outcome::isBad` and `Defect::hasOrigin` entirely.
 */
class OutcomeDefectOriginDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def documentedNames(doc: String, names: Set[String]): Set[String] =
    names.filter(n => s"\\b${java.util.regex.Pattern.quote(n)}\\b".r.findFirstIn(doc).isDefined)

  private def instanceMethodNames(c: Class[?]): Set[String] =
    c.getDeclaredMethods
      .filterNot(m => java.lang.reflect.Modifier.isStatic(m.getModifiers))
      .map(_.getName)
      .toSet

  private lazy val outcomeNames: Set[String] = instanceMethodNames(classOf[onion.Outcome[?]])
  private val defectNames: Set[String] = Set("hasOrigin", "onLine", "under", "describe")
  private val originNames: Set[String] = Set("hasColumn", "onLine", "inSource", "describe")

  it("actual onion.Outcome exposes the names this guard assumes (sanity check)") {
    assert(outcomeNames.nonEmpty, "reflection on onion.Outcome found no instance members -- the scan has rotted")
  }

  it("actual onion.Defect declares the methods this guard assumes (sanity check)") {
    val declared = classOf[onion.Defect].getDeclaredMethods.map(_.getName).toSet
    assert(defectNames.subsetOf(declared),
      s"onion.Defect no longer declares: ${(defectNames -- declared).toSeq.sorted.mkString(", ")} -- the scan has rotted")
  }

  it("actual onion.Origin declares the methods this guard assumes (sanity check)") {
    val declared = classOf[onion.Origin].getDeclaredMethods.map(_.getName).toSet
    assert(originNames.subsetOf(declared),
      s"onion.Origin no longer declares: ${(originNames -- declared).toSeq.sorted.mkString(", ")} -- the scan has rotted")
  }

  it("docs/reference/stdlib.md documents every onion.Outcome member") {
    val doc = read("docs/reference/stdlib.md")
    val missing = outcomeNames -- documentedNames(doc, outcomeNames)
    assert(missing.isEmpty, s"docs/reference/stdlib.md is missing Outcome members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md documents every onion.Outcome member") {
    val doc = read("docs/ja/reference/stdlib.md")
    val missing = outcomeNames -- documentedNames(doc, outcomeNames)
    assert(missing.isEmpty, s"docs/ja/reference/stdlib.md is missing Outcome members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/reference/stdlib.md documents every onion.Defect member") {
    val doc = read("docs/reference/stdlib.md")
    val missing = defectNames -- documentedNames(doc, defectNames)
    assert(missing.isEmpty, s"docs/reference/stdlib.md is missing Defect members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md documents every onion.Defect member") {
    val doc = read("docs/ja/reference/stdlib.md")
    val missing = defectNames -- documentedNames(doc, defectNames)
    assert(missing.isEmpty, s"docs/ja/reference/stdlib.md is missing Defect members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/reference/stdlib.md documents every onion.Origin member") {
    val doc = read("docs/reference/stdlib.md")
    val missing = originNames -- documentedNames(doc, originNames)
    assert(missing.isEmpty, s"docs/reference/stdlib.md is missing Origin members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md documents every onion.Origin member") {
    val doc = read("docs/ja/reference/stdlib.md")
    val missing = originNames -- documentedNames(doc, originNames)
    assert(missing.isEmpty, s"docs/ja/reference/stdlib.md is missing Origin members: ${missing.toSeq.sorted.mkString(", ")}")
  }
}
