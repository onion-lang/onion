package onion.compiler

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard tying `error.parsing.hint.*` keys referenced from the syntax-hint
 * classifiers to the two message bundles.
 *
 * Every `hint("error.parsing.hint.foo", ...)` call site in
 * `SyntaxHintClassifier`/`ControlFlowSyntaxHints` needs a matching property in
 * both `errorMessage.properties` (en) and `errorMessage_ja.properties` (ja) --
 * a typo'd or newly added key that only lands in one bundle would surface as a
 * `MissingResourceException` at runtime instead of a build-time failure. This
 * also flags the opposite drift: a `error.parsing.hint.*` property left behind
 * in a bundle after its call site was removed.
 */
class HintMessageKeyCoverageSpec extends AnyFunSpec {

  private val hintKeyPattern = """error\.parsing\.hint\.\w+""".r
  private val propertyKeyPattern = """^(error\.parsing\.hint\.\w+)=""".r

  private def keysReferencedIn(path: String): Set[String] =
    hintKeyPattern
      .findAllMatchIn(java.nio.file.Files.readString(java.nio.file.Path.of(path)))
      .map(_.matched)
      .toSet

  private def hintKeysDeclaredIn(path: String): Set[String] =
    java.nio.file.Files
      .readAllLines(java.nio.file.Path.of(path))
      .toArray(Array.empty[String])
      .flatMap(line => propertyKeyPattern.findFirstMatchIn(line).map(_.group(1)))
      .toSet

  private lazy val referencedKeys: Set[String] =
    keysReferencedIn("src/main/scala/onion/compiler/parser/SyntaxHintClassifier.scala") ++
      keysReferencedIn("src/main/scala/onion/compiler/parser/ControlFlowSyntaxHints.scala")

  private lazy val englishKeys: Set[String] =
    hintKeysDeclaredIn("src/main/resources/errorMessage.properties")

  private lazy val japaneseKeys: Set[String] =
    hintKeysDeclaredIn("src/main/resources/errorMessage_ja.properties")

  it("finds at least one error.parsing.hint.* call site (the scan has not rotted)") {
    assert(referencedKeys.nonEmpty)
  }

  it("has an English message for every hint key referenced by the classifiers") {
    val missing = (referencedKeys -- englishKeys).toSeq.sorted
    assert(missing.isEmpty, s"errorMessage.properties is missing: ${missing.mkString(", ")}")
  }

  it("has a Japanese message for every hint key referenced by the classifiers") {
    val missing = (referencedKeys -- japaneseKeys).toSeq.sorted
    assert(missing.isEmpty, s"errorMessage_ja.properties is missing: ${missing.mkString(", ")}")
  }

  it("has no orphaned error.parsing.hint.* property left over in the English bundle") {
    val orphaned = (englishKeys -- referencedKeys).toSeq.sorted
    assert(orphaned.isEmpty, s"errorMessage.properties has unreferenced keys: ${orphaned.mkString(", ")}")
  }

  it("has no orphaned error.parsing.hint.* property left over in the Japanese bundle") {
    val orphaned = (japaneseKeys -- referencedKeys).toSeq.sorted
    assert(orphaned.isEmpty, s"errorMessage_ja.properties has unreferenced keys: ${orphaned.mkString(", ")}")
  }
}
