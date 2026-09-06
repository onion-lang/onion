package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/**
 * `WarningCodeDocCoverageSpec` guards that every declared `WarningCategory` is mentioned
 * in the docs. It does not check whether the compiler ever actually reports that
 * category -- and seven of them never do: their `WarningReporter` convenience method
 * (`unusedImport`, `deprecatedFeature`, `emptyBlock`, `redundantCast`,
 * `possibleNullDereference`, `unnecessaryConversion`, `uncheckedCast`) is defined but
 * called from nowhere else in `src/main/scala`, so `--Wno unused-import` and friends name
 * a warning that can be suppressed but can never fire. Nothing told a reader of
 * docs/reference/error-codes.md's "W codes don't fail compilation by default... and can
 * be suppressed" framing that these particular codes are pure dead code, declared ahead
 * of an implementation that was never written.
 *
 * This is that guard, from both directions:
 *   - a category whose convenience method IS called from production code must not be
 *     marked reserved in the docs (an implemented warning claiming to be vaporware would
 *     mislead a reader the other way, and silently hide from `WarningEmissionSpec`-style
 *     regression coverage);
 *   - a category that is reserved today must still say so in every reserved-codes table,
 *     so implementing detection later is the only way to make this spec ask for the
 *     docs to be updated (delete the code from `reservedByConvention` here and from the
 *     doc's reserved markers in the same change).
 */
class ReservedWarningCodeSpec extends AnyFunSpec {

  // As of this writing, precisely these codes have no caller of their WarningReporter
  // convenience method anywhere in src/main/scala outside WarningReporter.scala itself.
  // Implementing one moves it out of this set *and* out of the doc's reserved markers.
  private val reservedByConvention: Set[String] =
    Set("W0002", "W0004", "W0007", "W0008", "W0009", "W0010", "W0011")

  private def readPath(p: Path): String = Files.readString(p)
  private def read(p: String): String = readPath(Path.of(p))

  private val warningScala = "src/main/scala/onion/compiler/Warning.scala"
  private val reporterScala = "src/main/scala/onion/compiler/WarningReporter.scala"

  /** category name (e.g. "UnusedImport") -> declared code (e.g. "W0002"). */
  private lazy val categoryToCode: Map[String, String] =
    """case (\w+)\s+extends WarningCategory\("(W\d+)"""".r
      .findAllMatchIn(read(warningScala))
      .map(m => m.group(1) -> m.group(2))
      .toMap

  /** convenience-method name (e.g. "unusedImport") -> category it reports, by scanning
   * each `def foo(...) = ... report(WarningCategory.Bar, ...)` block in WarningReporter. */
  private lazy val methodToCategory: Map[String, String] = {
    val src = read(reporterScala)
    val defStarts = """(?m)^\s{2}def (\w+)\(""".r.findAllMatchIn(src).map(m => (m.group(1), m.start)).toList
    val boundaries = defStarts.map(_._2).drop(1) :+ src.length
    defStarts.zip(boundaries).flatMap { case ((name, start), end) =>
      """report\(WarningCategory\.(\w+)""".r.findFirstMatchIn(src.substring(start, end)).map(m => name -> m.group(1))
    }.toMap
  }

  private lazy val mainScalaFiles: Seq[Path] = {
    val root = Path.of("src/main/scala")
    val excluded = root.resolve("onion/compiler/WarningReporter.scala")
    val stream = Files.walk(root)
    try stream.iterator().asScala.filter(p => p.toString.endsWith(".scala") && p != excluded).toSeq
    finally stream.close()
  }

  private lazy val mainScalaSources: String = mainScalaFiles.map(readPath).mkString(" ")

  private def isWired(method: String): Boolean = mainScalaSources.contains(s".$method(")

  /** Codes whose convenience method is (from this scan) never called outside WarningReporter. */
  private lazy val computedReserved: Set[String] =
    methodToCategory.collect { case (method, category) if !isWired(method) => categoryToCode(category) }.toSet

  it("scans WarningReporter/Warning.scala successfully (sanity check)") {
    assert(categoryToCode.size >= 16, s"expected at least 16 declared categories, found ${categoryToCode.size}")
    assert(methodToCategory.size >= 15, s"expected at least 15 convenience methods, found ${methodToCategory.size}: ${methodToCategory.keys}")
  }

  it("the reserved set recorded here matches what is actually never called from production code") {
    assert(computedReserved == reservedByConvention,
      s"reservedByConvention is stale: computed=${computedReserved.toSeq.sorted}, " +
      s"recorded=${reservedByConvention.toSeq.sorted} -- update this test's set (and the " +
      "matching doc markers) to reflect which codes are now wired or newly dead")
  }

  private def assertMarkedReserved(path: String): Unit = {
    val doc = read(path)
    def marksReserved(line: String) = line.toLowerCase.contains("reserved") || line.contains("予約")
    val unmarked = reservedByConvention.filterNot { code =>
      // Any line mentioning the code must also mark it reserved (English or Japanese).
      doc.linesIterator.filter(_.contains(code)).forall(marksReserved)
    }
    assert(unmarked.isEmpty,
      s"$path mentions ${unmarked.toSeq.sorted.mkString(", ")} without marking them reserved -- " +
      "these codes are declared and can be named with --Wno, but no code path ever reports them")
  }

  it("docs/reference/error-codes.md marks every never-emitted code reserved") {
    assertMarkedReserved("docs/reference/error-codes.md")
  }

  it("docs/ja/reference/error-codes.md marks every never-emitted code reserved") {
    assertMarkedReserved("docs/ja/reference/error-codes.md")
  }

  it("docs/tools/compiler.md marks every never-emitted code reserved") {
    assertMarkedReserved("docs/tools/compiler.md")
  }

  it("docs/ja/tools/compiler.md marks every never-emitted code reserved") {
    assertMarkedReserved("docs/ja/tools/compiler.md")
  }
}
