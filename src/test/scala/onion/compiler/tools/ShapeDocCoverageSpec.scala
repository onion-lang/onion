package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `Shape`/`Shapes`/`Lossless` docs.
 *
 * `onion.Shape` is a public interface whose instance methods (`parse`, `print`,
 * `canPrint`, `describe`, `isLossless`, `parseLossless`, `printLossless`, `eachLine`,
 * `lines`, `sepBy`, `xmap`, `orElse`) are declared or defaulted directly on it;
 * `onion.Lossless` is the lens type its `parseLossless` returns; `onion.Shapes` is the
 * static factory (`regex`/`json`/`config`/`yaml`) behind the `shape name = ...` sugar.
 * All three are documented together under one `## Shape` section in
 * docs/reference/stdlib.md and docs/ja/reference/stdlib.md.
 *
 * Unlike the other `*DocCoverageSpec`s, this one checks each member's presence
 * *within that section's text* (up to the next `##` heading) rather than anywhere in
 * the file: a whole-file substring check for `describe` passes trivially because
 * `Origin::describe`/`Defect::describe` are documented elsewhere in the same file,
 * which is exactly how `Shape::describe()` went undocumented unnoticed.
 */
class ShapeDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def shapeSection(doc: String): String = {
    val start = doc.indexOf("\n## Shape")
    assert(start >= 0, "no '## Shape' section found")
    val next = doc.indexOf("\n## ", start + 1)
    if (next >= 0) doc.substring(start, next) else doc.substring(start)
  }

  private def missingFrom(section: String, names: Set[String]): Set[String] =
    names.filterNot(n => s"\\b${java.util.regex.Pattern.quote(n)}\\b".r.findFirstIn(section).isDefined)

  private def instanceMethodNames(c: Class[?]): Set[String] =
    c.getDeclaredMethods
      .filterNot(m => java.lang.reflect.Modifier.isStatic(m.getModifiers))
      .map(_.getName)
      .toSet

  private lazy val shapeNames: Set[String] = instanceMethodNames(classOf[onion.Shape[?]])
  private lazy val losslessNames: Set[String] = instanceMethodNames(classOf[onion.Lossless[?]]) -- Set("toString")
  private val shapesFactoryNames: Set[String] = Set("regex", "json", "config", "yaml")

  it("actual onion.Shape exposes the names this guard assumes (sanity check)") {
    assert(shapeNames.nonEmpty, "reflection on onion.Shape found no instance members -- the scan has rotted")
  }

  it("actual onion.Lossless exposes the names this guard assumes (sanity check)") {
    assert(losslessNames.nonEmpty, "reflection on onion.Lossless found no instance members -- the scan has rotted")
  }

  it("actual onion.Shapes declares the factory methods this guard assumes (sanity check)") {
    val declared = classOf[onion.Shapes].getDeclaredMethods
      .filter(m => java.lang.reflect.Modifier.isStatic(m.getModifiers) && java.lang.reflect.Modifier.isPublic(m.getModifiers))
      .map(_.getName)
      .toSet
    assert(shapesFactoryNames.subsetOf(declared),
      s"onion.Shapes no longer declares: ${(shapesFactoryNames -- declared).toSeq.sorted.mkString(", ")} -- the scan has rotted")
  }

  it("docs/reference/stdlib.md's Shape section documents every onion.Shape member") {
    val section = shapeSection(read("docs/reference/stdlib.md"))
    val missing = missingFrom(section, shapeNames)
    assert(missing.isEmpty, s"docs/reference/stdlib.md's Shape section is missing Shape members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md's Shape section documents every onion.Shape member") {
    val section = shapeSection(read("docs/ja/reference/stdlib.md"))
    val missing = missingFrom(section, shapeNames)
    assert(missing.isEmpty, s"docs/ja/reference/stdlib.md's Shape section is missing Shape members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/reference/stdlib.md's Shape section documents every onion.Lossless member") {
    val section = shapeSection(read("docs/reference/stdlib.md"))
    val missing = missingFrom(section, losslessNames)
    assert(missing.isEmpty, s"docs/reference/stdlib.md's Shape section is missing Lossless members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md's Shape section documents every onion.Lossless member") {
    val section = shapeSection(read("docs/ja/reference/stdlib.md"))
    val missing = missingFrom(section, losslessNames)
    assert(missing.isEmpty, s"docs/ja/reference/stdlib.md's Shape section is missing Lossless members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/reference/stdlib.md's Shape section documents every Shapes:: factory method") {
    val section = shapeSection(read("docs/reference/stdlib.md"))
    val missing = shapesFactoryNames.filterNot(n => section.contains(s"Shapes::$n"))
    assert(missing.isEmpty, s"docs/reference/stdlib.md's Shape section doesn't mention Shapes::${missing.mkString(", Shapes::")}")
  }

  it("docs/ja/reference/stdlib.md's Shape section documents every Shapes:: factory method") {
    val section = shapeSection(read("docs/ja/reference/stdlib.md"))
    val missing = shapesFactoryNames.filterNot(n => section.contains(s"Shapes::$n"))
    assert(missing.isEmpty, s"docs/ja/reference/stdlib.md's Shape section doesn't mention Shapes::${missing.mkString(", Shapes::")}")
  }
}
