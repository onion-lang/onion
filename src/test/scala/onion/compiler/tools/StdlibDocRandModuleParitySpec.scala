package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for the "Rand Module" section of docs/reference/stdlib.md (and its
 * Japanese translation) against `onion.Rand`'s public API. Several public static
 * methods on the class (see src/main/java/onion/Rand.java) were never mentioned
 * in either reference's Rand Module section, making them undiscoverable without
 * reading the Java source.
 */
class StdlibDocRandModuleParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def sectionUnder(doc: String, heading: String): String = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == heading)
    assert(start >= 0, s"could not find heading '$heading' — the scan has rotted")
    lines.drop(start + 1).takeWhile(!_.trim.startsWith("## ")).mkString("\n")
  }

  private val methods = Seq(
    "nextInt", "nextLong", "nextDouble", "nextBoolean",
    "choice", "shuffle", "sample", "uuid"
  )

  // nextInt/nextLong/nextDouble each have a bounded overload beyond the zero-arg
  // form already covered by `methods` above; a name-only `contains` check can't
  // tell the zero-arg mention from the bounded one, so check the bounded heading
  // each sibling documents under.
  private val boundedMethods = Seq("nextInt", "nextLong", "nextDouble")

  it("mentions every public onion.Rand method in the English Rand Module section") {
    val en = sectionUnder(read("docs/reference/stdlib.md"), "## Rand Module")
    methods.foreach { name =>
      assert(en.contains(s"Rand::$name"),
        s"docs/reference/stdlib.md's Rand Module section doesn't mention Rand::$name, " +
        "a public onion.Rand method")
    }
  }

  it("mentions every public onion.Rand method in the Japanese Rand Module section") {
    val ja = sectionUnder(read("docs/ja/reference/stdlib.md"), "## Rand モジュール")
    methods.foreach { name =>
      assert(ja.contains(s"Rand::$name"),
        s"docs/ja/reference/stdlib.md's Rand モジュール section doesn't mention Rand::$name, " +
        "a public onion.Rand method")
    }
  }

  it("documents the bounded overload of every Rand method that has one, in English") {
    val en = sectionUnder(read("docs/reference/stdlib.md"), "## Rand Module")
    boundedMethods.foreach { name =>
      assert(en.contains(s"### Rand::$name (bounded)"),
        s"docs/reference/stdlib.md's Rand Module section doesn't document the bounded " +
        s"overload of Rand::$name (expected a '### Rand::$name (bounded)' subsection)")
    }
  }

  it("documents the bounded overload of every Rand method that has one, in Japanese") {
    val ja = sectionUnder(read("docs/ja/reference/stdlib.md"), "## Rand モジュール")
    boundedMethods.foreach { name =>
      assert(ja.contains(s"### Rand::$name（範囲指定）"),
        s"docs/ja/reference/stdlib.md's Rand モジュール section doesn't document the bounded " +
        s"overload of Rand::$name (expected a '### Rand::$name（範囲指定）' subsection)")
    }
  }
}
