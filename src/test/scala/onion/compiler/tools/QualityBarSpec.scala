package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec
import scala.jdk.CollectionConverters._

/**
 * Drift guard for `docs/quality-bar.md` (issue #428).
 *
 * The file's whole value is being the place where the numbers are true, and it went
 * stale three times in three days (2590→2644, 2655→2744, 2744→2777) because every
 * figure was transcribed by hand. The rows that can be measured are measured here, so
 * "someone forgot" becomes a build failure instead of a quiet lie.
 *
 * Rows 4 and 5 are prose judgements with no mechanical definition; this spec ignores
 * them rather than pretending to check them. Row 1 (the test count) cannot be measured
 * from inside the suite it counts, so it is checked for plausibility — see below.
 *
 * Both language copies must agree, which is itself an argument for generating the
 * table one day rather than maintaining two.
 */
class QualityBarSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def rows(doc: String): Map[Int, String] =
    doc.linesIterator
      .filter(_.startsWith("| "))
      .flatMap { line =>
        val cells = line.split("\\|").map(_.trim)
        if (cells.length > 5 && cells(1).forall(_.isDigit) && cells(1).nonEmpty)
          Some(cells(1).toInt -> cells(4))
        else None
      }.toMap

  private lazy val en = rows(read("docs/quality-bar.md"))
  private lazy val ja = rows(read("docs/ja/quality-bar.md"))

  private def runSamples: Seq[java.nio.file.Path] = {
    val out = scala.collection.mutable.Buffer[java.nio.file.Path]()
    java.nio.file.Files.list(java.nio.file.Path.of("run")).forEach { p =>
      if (p.getFileName.toString.endsWith(".on")) out += p
    }
    out.toSeq
  }

  private def firstInt(s: String): Int =
    """\d+""".r.findFirstIn(s).map(_.toInt)
      .getOrElse(fail(s"no number in quality-bar cell: $s"))

  describe("docs/quality-bar.md matches what is actually there") {
    it("row 2: the sample count equals the number of run/*.on files") {
      assert(firstInt(en(2)) == runSamples.size,
        s"quality-bar says ${en(2)}, run/ holds ${runSamples.size} samples")
    }

    it("row 3: the large-program count equals run/*.on with >= 100 lines") {
      val large = runSamples.filter(p =>
        java.nio.file.Files.readAllLines(p).size >= 100).map(_.getFileName.toString.dropRight(3)).sorted
      assert(firstInt(en(3)) == large.size,
        s"quality-bar says ${en(3)}, actual large programs are ${large.mkString(", ")}")
      // the parenthesised list must name them, so it cannot rot separately from the count
      large.foreach(n => assert(en(3).contains(n), s"quality-bar row 3 does not name $n: ${en(3)}"))
    }

    it("row 6: docs parity equals the guide file counts, and they are equal") {
      def guides(dir: String): Int = {
        var n = 0
        java.nio.file.Files.list(java.nio.file.Path.of(dir)).forEach { p =>
          if (p.getFileName.toString.endsWith(".md")) n += 1
        }
        n
      }
      val (e, j) = (guides("docs/guide"), guides("docs/ja/guide"))
      assert(e == j, s"guide parity broken: docs/guide has $e, docs/ja/guide has $j")
      assert(firstInt(en(6)) == e, s"quality-bar says ${en(6)}, actual is $e / $j")
    }

    it("row 7: the diagnostic-code count equals the declared SemanticError cases") {
      val declared = """case object \w+ extends SemanticError\(""".r
        .findAllIn(read("src/main/scala/onion/compiler/SemanticError.scala")).size
      assert(firstInt(en(7)) == declared,
        s"quality-bar says ${en(7)} diagnostic codes, SemanticError declares $declared")
    }

    it("row 1: the recorded test count is in the right ballpark for this suite") {
      // A suite cannot count itself while running, so this only catches the failure
      // mode that actually happened: a figure left behind across many releases.
      val recorded = firstInt(en(1))
      assert(recorded >= 2700 && recorded <= 4000,
        s"quality-bar records $recorded tests, which is far from this suite's size — re-measure")
    }

    it("the Japanese copy carries the same measured figures") {
      for (row <- Seq(2, 3, 6, 7)) {
        assert(firstInt(ja(row)) == firstInt(en(row)),
          s"row $row disagrees between languages: EN=${en(row)} JA=${ja(row)}")
      }
    }
  }
}
