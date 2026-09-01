package onion.compiler.effects

import org.scalatest.funspec.AnyFunSpec

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

/**
 * Drift guard tying `docs/reference/effects.md` (and its `ja` translation) to
 * `effect-table.txt`. The doc hand-enumerates which stdlib classes carry a real
 * (non-`pure`) effect somewhere in the table, e.g. "Files, FileResource, Proc, Http,
 * ...". Nothing checked that list against the table itself, so a class added to the
 * table with a real effect (as happened for `Archive`, `Db`, `Net`, `Server`, `Future`
 * and `Concurrent`, and the `ToolCli` CLI-generator effects) could go unmentioned in
 * both docs indefinitely -- exactly the drift `SemanticErrorCategoryDocCoverageSpec`
 * and `ErrorCodeDocCoverageSpec` already guard against for other hand-written lists.
 */
class EffectTableDocCoverageSpec extends AnyFunSpec {

  private def effectfulOnionClasses: Set[String] = {
    val lines = Files.readAllLines(Path.of("src/main/resources/onion/effect-table.txt")).asScala.iterator
    val parsed = EffectTable.parseLines(lines)
    val fromWildcards = parsed.wildcard.collect { case (cls, effects) if effects.nonEmpty => cls }
    val fromExact = parsed.exact.collect { case ((cls, _), effects) if effects.nonEmpty => cls }
    (fromWildcards ++ fromExact).toSet
      .filter(_.startsWith("onion."))
      .map(_.stripPrefix("onion."))
      .map(_.takeWhile(_ != '$')) // Db$Conn, Net$Listener, Concurrent$Pool -> outer class
  }

  private def assertDocMentionsAll(docPath: String): Unit = {
    val text = Files.readString(Path.of(docPath))
    val classes = effectfulOnionClasses
    assert(classes.nonEmpty, "effect-table.txt scan found no effectful onion.* classes -- the scan has rotted")
    val missing = classes.filterNot(cls => text.contains(cls))
    assert(missing.isEmpty,
      s"$docPath's stdlib class list is missing effectful classes from effect-table.txt: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/reference/effects.md mentions every effectful onion.* stdlib class") {
    assertDocMentionsAll("docs/reference/effects.md")
  }

  it("docs/ja/reference/effects.md mentions every effectful onion.* stdlib class") {
    assertDocMentionsAll("docs/ja/reference/effects.md")
  }
}
