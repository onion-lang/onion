package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for the `## Shape` section of docs/reference/stdlib.md (and its Japanese
 * translation) against `onion.Shape`'s public API. The section covered `parse`,
 * `print`, `canPrint`, and the L1/L2 laws, but never mentioned the lossless-shapes /
 * lens API (`isLossless`, `parseLossless`, `printLossless`, `Lossless`, `Residue`), the
 * combinators (`eachLine`, `lines`, `sepBy`, `xmap`, `orElse`), or the `Shapes::config`
 * / `Shapes::yaml` factories (see src/main/java/onion/Shape.java, Lossless.java,
 * Shapes.java) — making a real, actively-used feature undiscoverable from the API
 * reference doc a user would actually search.
 */
class StdlibDocShapeModuleParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def sectionUnder(doc: String, heading: String): String = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == heading)
    assert(start >= 0, s"could not find heading '$heading' — the scan has rotted")
    lines.drop(start + 1).takeWhile(!_.trim.startsWith("## ")).mkString("\n")
  }

  private val members = Seq(
    "isLossless", "parseLossless", "printLossless",
    "eachLine", "sepBy", "xmap", "orElse",
    "Lossless", "Residue",
    "Shapes::config", "Shapes::yaml"
  )

  it("mentions every lossless-shape/lens/combinator member in the English stdlib reference's Shape section") {
    val en = sectionUnder(read("docs/reference/stdlib.md"), "## Shape")
    members.foreach { name =>
      assert(en.contains(name),
        s"docs/reference/stdlib.md's Shape section doesn't mention $name, " +
        "a public onion.Shape/Lossless/Shapes member")
    }
  }

  it("mentions every lossless-shape/lens/combinator member in the Japanese stdlib reference's Shape section") {
    val ja = sectionUnder(read("docs/ja/reference/stdlib.md"), "## Shape")
    members.foreach { name =>
      assert(ja.contains(name),
        s"docs/ja/reference/stdlib.md's Shape section doesn't mention $name, " +
        "a public onion.Shape/Lossless/Shapes member")
    }
  }
}
