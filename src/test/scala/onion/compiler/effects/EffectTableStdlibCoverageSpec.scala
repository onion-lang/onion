package onion.compiler.effects

import org.scalatest.funspec.AnyFunSpec

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

/**
 * Drift guard for the effect table (issue #429).
 *
 * `effect-table.txt` classifies `onion.*` stdlib classes by hand, and nothing checks
 * that it keeps up with the `.java` sources under `src/main/java/onion`. This spec fails when a stdlib
 * class has no `onion.Class#*=` wildcard and no `onion.Class#method=` entry at all,
 * which is exactly how `onion.Residue` (shipped in v0.10.0, #362) went unregistered
 * and would silently resolve every one of its (currently nonexistent) methods to
 * `Effect.Unknown`.
 */
class EffectTableStdlibCoverageSpec extends AnyFunSpec {

  private def stdlibClassNames: Set[String] = {
    val dir = Path.of("src/main/java/onion")
    Files.list(dir).iterator().asScala
      .map(_.getFileName.toString)
      .filter(_.endsWith(".java"))
      .map(_.stripSuffix(".java"))
      .toSet
  }

  private def registeredClasses: Set[String] = {
    val lines = Files.readAllLines(Path.of("src/main/resources/onion/effect-table.txt")).asScala.iterator
    val parsed = EffectTable.parseLines(lines)
    val fromWildcards = parsed.wildcard.keySet
    val fromExact = parsed.exact.keySet.map(_._1)
    (fromWildcards ++ fromExact)
      .filter(_.startsWith("onion."))
      .map(_.stripPrefix("onion."))
  }

  it("every src/main/java/onion/*.java class has an effect-table.txt entry") {
    val classes = stdlibClassNames
    assert(classes.nonEmpty, "source scan found no onion.* stdlib classes — the scan has rotted")
    val missing = classes -- registeredClasses
    assert(missing.isEmpty,
      s"onion.* stdlib classes with no effect-table.txt entry (add a Class#*= or Class#method= row): ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("the table names no onion.* class that no longer exists") {
    val table = java.nio.file.Files.readString(
      java.nio.file.Path.of("src/main/resources/onion/effect-table.txt"))
    val declared = """(?m)^onion\.([A-Za-z0-9_]+)#""".r
      .findAllMatchIn(table).map(_.group(1)).toSet
    val present = {
      val out = scala.collection.mutable.Set[String]()
      java.nio.file.Files.list(java.nio.file.Path.of("src/main/java/onion")).forEach { p =>
        val n = p.getFileName.toString
        if (n.endsWith(".java")) out += n.dropRight(5)
      }
      out.toSet
    }
    assert((declared -- present).isEmpty,
      s"effect-table entries for classes that no longer exist: ${(declared -- present).mkString(", ")}")
  }

  it("the whole table parses, so a malformed edit fails here rather than at first use") {
    import scala.jdk.CollectionConverters._
    val lines = java.nio.file.Files.readAllLines(
      java.nio.file.Path.of("src/main/resources/onion/effect-table.txt")).asScala
    EffectTable.parseLines(lines.iterator)
  }
}
