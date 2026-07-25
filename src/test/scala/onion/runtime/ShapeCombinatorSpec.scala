package onion.runtime

import onion.{Function1, Function2, Origin, Outcome, Shape, Shapes}
import org.scalatest.funspec.AnyFunSpec

import scala.jdk.CollectionConverters._

/**
 * A shape that does not compose is a macro. These are what make `Shape` an abstraction:
 * one declarative boundary description, combined rather than re-derived per format.
 *
 * The important one is the per-line read. Today `parseAll` guards its accumulator with a
 * null check and *no else branch* (`Rewriting.scala:683-688`) and returns `List[R]`, so a
 * caller cannot learn how many lines were dropped, which ones, or why. A thousand-line
 * log with five bad lines silently becomes nine hundred and ninety-five rows.
 */
class ShapeCombinatorSpec extends AnyFunSpec {

  private case class Pt(x: Int, y: Int)

  private def jlist[A](xs: Seq[A]): java.util.List[A] = xs.toList.asJava

  private val point: Shape[Pt] = Shapes.regex(
    "(-?\\d+),(-?\\d+)",
    jlist(Seq("x", "y")),
    jlist(Seq("Int", "Int")),
    ((parts: java.util.List[Object]) =>
      Pt(parts.get(0).asInstanceOf[Int], parts.get(1).asInstanceOf[Int])): Function1[java.util.List[Object], Pt],
    ((p: Pt) => s"${p.x},${p.y}"): Function1[Pt, String]
  )

  private val src = Origin.atLine("points.txt", 1)

  describe("eachLine — the good rows and the bad ones, both") {
    val text = "1,2\nbroken\n3,4\nalso bad\n5,6"

    it("returns one outcome per line") {
      assert(point.eachLine(text, src).size == 5)
    }

    it("keeps the rows it could read") {
      val rows = Outcome.values(point.eachLine(text, src))
      assert(rows.asScala.toList == List(Pt(1, 2), Pt(3, 4), Pt(5, 6)))
    }

    it("reports the lines it could not, with their line numbers") {
      // This is the whole point: today these two lines vanish without trace.
      val bad = Outcome.defects(point.eachLine(text, src)).asScala.toList
      assert(bad.size == 2)
      assert(bad.map(_.origin.line) == List(2, 4), bad.map(_.describe).mkString("; "))
      assert(bad.head.origin.source == "points.txt")
    }

    it("scales to the shape of the demo: 1000 lines, 5 broken") {
      val lines = (1 to 1000).map(i => if (i % 200 == 0) "broken" else s"$i,$i")
      val outcomes = point.eachLine(lines.mkString("\n"), src)
      assert(Outcome.values(outcomes).size == 995)
      val defects = Outcome.defects(outcomes).asScala.toList
      assert(defects.size == 5)
      assert(defects.map(_.origin.line) == List(200, 400, 600, 800, 1000))
    }
  }

  describe("lines — all or nothing, for when a partial result is meaningless") {
    it("reads every line into a list") {
      val r = point.lines.parse("1,2\n3,4", src)
      assert(r.isOk)
      assert(r.get.asScala.toList == List(Pt(1, 2), Pt(3, 4)))
    }

    it("fails with every bad line's defect, not just the first") {
      val r = point.lines.parse("1,2\nbroken\n3,4\nalso bad", src)
      assert(r.isBad)
      assert(r.defects.size == 2)
      assert(r.defects.asScala.map(_.origin.line).toList == List(2, 4))
    }

    it("prints a list back, one per line") {
      assert(point.lines.canPrint)
      assert(point.lines.print(jlist(Seq(Pt(1, 2), Pt(3, 4)))) == "1,2\n3,4")
    }

    it("satisfies L1 for the list shape") {
      val vs = jlist(Seq(Pt(0, 0), Pt(-1, 2), Pt(30, -40)))
      assert(point.lines.parse(point.lines.print(vs), src).get == vs)
    }
  }

  describe("sepBy — repetition with a literal separator") {
    it("reads a separated list") {
      val r = point.sepBy(" | ").parse("1,2 | 3,4 | 5,6", src)
      assert(r.isOk)
      assert(r.get.asScala.toList == List(Pt(1, 2), Pt(3, 4), Pt(5, 6)))
    }

    it("satisfies L1") {
      val shape = point.sepBy("; ")
      val vs = jlist(Seq(Pt(1, 2), Pt(-3, 4)))
      assert(shape.parse(shape.print(vs), src).get == vs)
    }

    it("treats the separator as literal text, not a pattern") {
      // A regex separator would have no unique rendering, so print could not exist.
      val shape = point.sepBy(".")
      assert(shape.parse("1,2.3,4", src).get.asScala.toList == List(Pt(1, 2), Pt(3, 4)))
    }

    it("reports every bad element, not just the first") {
      val r = point.sepBy(",,").parse("bad,,1,2,,worse", src)
      assert(r.isBad)
      assert(r.defects.size == 2, r.describe)
    }
  }

  describe("xmap — an isomorphism, so print survives") {
    // A one-way `map` would silently destroy print. Requiring both directions is the
    // type telling the truth about what a bidirectional shape needs.
    val swapped: Shape[Pt] = point.xmap(
      ((p: Pt) => Pt(p.y, p.x)): Function1[Pt, Pt],
      ((p: Pt) => Pt(p.y, p.x)): Function1[Pt, Pt]
    )

    it("maps the parsed value") {
      assert(swapped.parse("1,2", src).get == Pt(2, 1))
    }

    it("preserves L1") {
      assert(swapped.parse(swapped.print(Pt(7, 9)), src).get == Pt(7, 9))
    }

    it("stays read-only when the underlying shape is") {
      val readOnly: Shape[Pt] = Shapes.regex(
        "(-?\\d+),(-?\\d+)", jlist(Seq("x", "y")), jlist(Seq("Int", "Int")),
        ((parts: java.util.List[Object]) =>
          Pt(parts.get(0).asInstanceOf[Int], parts.get(1).asInstanceOf[Int])): Function1[java.util.List[Object], Pt],
        null
      )
      assert(!readOnly.xmap(
        ((p: Pt) => p): Function1[Pt, Pt], ((p: Pt) => p): Function1[Pt, Pt]).canPrint)
    }
  }

  describe("orElse — first success, both failures") {
    val alt: Shape[Pt] = Shapes.regex(
      "\\((-?\\d+) (-?\\d+)\\)", jlist(Seq("x", "y")), jlist(Seq("Int", "Int")),
      ((parts: java.util.List[Object]) =>
        Pt(parts.get(0).asInstanceOf[Int], parts.get(1).asInstanceOf[Int])): Function1[java.util.List[Object], Pt],
      ((p: Pt) => s"(${p.x} ${p.y})"): Function1[Pt, String]
    )

    it("takes the first shape that reads") {
      assert(point.orElse(alt).parse("1,2", src).get == Pt(1, 2))
      assert(point.orElse(alt).parse("(3 4)", src).get == Pt(3, 4))
    }

    it("reports both failures when neither reads, so the user can see both spellings") {
      val r = point.orElse(alt).parse("nonsense", src)
      assert(r.isBad)
      assert(r.defects.size == 2, r.describe)
    }

    it("prints with the first shape") {
      assert(point.orElse(alt).print(Pt(1, 2)) == "1,2")
    }
  }
}
