package onion.runtime

import onion.{Defect, Function1, Origin, Outcome, Shape, Shapes}
import org.scalatest.funspec.AnyFunSpec

import scala.jdk.CollectionConverters._

/**
 * `Shape[T]` is the declarative data boundary: a partial, potentially bidirectional
 * correspondence between external text and a typed value.
 *
 * Onion has four mechanisms that each approximate this — `from re"..."`, `derive!`,
 * auto-CLI and the resource literals — sharing no vocabulary and disagreeing about how
 * failure is reported. This is the type they were approximating.
 *
 * Two laws matter and must not be conflated:
 *
 *   L1 (round-trip)    parse(print(v)) == Ok(v)      — guaranteed when print exists
 *   L2 (normalization) print(parse(t)) == t          — FALSE in general: "007" parses
 *                                                       to 7 and prints as "7"
 *
 * L2 is what "lossless" means, and it is rare; most shapes are L1-only. Keeping them
 * apart is the substance behind calling the language reversible.
 */
class ShapeSpec extends AnyFunSpec {

  /** A point, standing in for the record a `shape` declaration would build. */
  private case class Pt(x: Int, y: Int)

  private def build: Function1[java.util.List[Object], Pt] =
    (parts: java.util.List[Object]) =>
      Pt(parts.get(0).asInstanceOf[Int], parts.get(1).asInstanceOf[Int])

  private def printer: Function1[Pt, String] =
    (p: Pt) => s"${p.x},${p.y}"

  private def tags(ts: String*): java.util.List[String] = ts.toList.asJava
  private def names(ns: String*): java.util.List[String] = ns.toList.asJava

  private val point: Shape[Pt] =
    Shapes.regex("(-?\\d+),(-?\\d+)", names("x", "y"), tags("Int", "Int"), build, printer)

  private val here = Origin.at("pt.txt", 1, 1)

  describe("parsing") {
    it("reads a value out of matching text") {
      val r = point.parse("3,4", here)
      assert(r.isOk)
      assert(r.get == Pt(3, 4))
    }

    it("is anchored — trailing junk is not a match") {
      assert(point.parse("3,4 and more", here).isBad)
    }

    it("reports where a non-match happened") {
      val r = point.parse("nope", here)
      assert(r.isBad)
      val d = r.defects.get(0)
      assert(d.origin == here, s"lost the origin: ${d.origin}")
    }

    it("distinguishes a conversion failure from a non-match") {
      // Today both return a bare `null`, so a caller cannot tell "this line is not a
      // record" from "this line is a record with a broken field".
      val noMatch = point.parse("nope", here).defects.get(0)
      val badField = Shapes
        .regex("(\\w+),(\\w+)", names("x", "y"), tags("Int", "Int"), build, printer)
        .parse("abc,4", here).defects.get(0)
      assert(noMatch.path.isEmpty, "a non-match belongs to the whole text, not a field")
      assert(badField.path == "x")
      assert(badField.expected == "Int")
      assert(badField.actual == "abc")
    }
  }

  describe("accumulation across components") {
    it("reports every bad field, not just the first") {
      val loose = Shapes.regex("(\\w+),(\\w+)", names("x", "y"), tags("Int", "Int"), build, printer)
      val r = loose.parse("abc,def", here)
      assert(r.isBad)
      assert(r.defects.size == 2, s"only reported ${r.defects.size}: ${r.describe}")
      assert(r.defects.get(0).path == "x")
      assert(r.defects.get(1).path == "y")
    }
  }

  describe("printing and L1") {
    it("renders a value back") {
      assert(point.canPrint)
      assert(point.print(Pt(3, 4)) == "3,4")
    }

    it("satisfies parse(print(v)) == Ok(v)") {
      for (p <- Seq(Pt(0, 0), Pt(3, 4), Pt(-1, -2), Pt(Int.MaxValue, Int.MinValue))) {
        assert(point.parse(point.print(p), here).get == p, s"round-trip failed for $p")
      }
    }

    it("does NOT claim parse-then-print is the identity") {
      // L2 is false in general and the type must not pretend otherwise: "007" is a
      // perfectly good Int that does not print back as itself.
      assert(point.parse("007,4", here).get == Pt(7, 4))
      assert(point.print(Pt(7, 4)) == "7,4")
    }

    it("says so when it cannot print, instead of failing later") {
      val readOnly: Shape[Pt] =
        Shapes.regex("(-?\\d+),(-?\\d+)", names("x", "y"), tags("Int", "Int"), build, null)
      assert(!readOnly.canPrint)
      val thrown = intercept[UnsupportedOperationException](readOnly.print(Pt(1, 2)))
      // The message has to name the shape, or a user cannot tell which one to fix.
      assert(thrown.getMessage.contains("(-?\\d+),(-?\\d+)"), thrown.getMessage)
    }
  }

  describe("positions inside a larger document") {
    it("lifts a defect onto the line it came from") {
      val r = point.parse("nope", Origin.atLine("log.txt", 1)).onLine(40)
      assert(r.defects.get(0).origin.line == 40)
      assert(r.defects.get(0).origin.source == "log.txt")
    }
  }
}
