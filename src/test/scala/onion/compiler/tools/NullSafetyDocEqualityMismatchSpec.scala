package onion.compiler.tools

/**
 * docs/ja/guide/null-safety.md has a section explaining that `==` on a
 * statically-nullable operand is null-safe *value* equality (java.util.Objects.equals:
 * both-null equal, one-null not-equal, otherwise structural equals), with `===`
 * available for reference identity — a real, tested feature (NullableEqualitySpec).
 * The English guide never mentioned this at all, so an English-only reader had no
 * way to learn it. This guards that the English guide documents it too, with a
 * compiling example that actually produces the claimed output.
 */
class NullSafetyDocEqualityMismatchSpec extends AbstractShellSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def sectionUnder(doc: String, heading: String): String = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == heading)
    assert(start >= 0, s"could not find heading '$heading' — the scan has rotted")
    val rest = lines.drop(start + 1)
    val end = rest.indexWhere(_.trim.startsWith("#"))
    (if (end >= 0) rest.take(end) else rest).mkString("\n")
  }

  private def codeFences(section: String): Seq[String] = {
    val lines = section.linesIterator.toSeq
    val fences = scala.collection.mutable.ArrayBuffer[String]()
    var i = 0
    while (i < lines.length) {
      if (lines(i).trim.startsWith("```")) {
        val bodyStart = i + 1
        val bodyEnd = lines.indexWhere(_.trim.startsWith("```"), bodyStart)
        fences += lines.slice(bodyStart, bodyEnd).mkString("\n")
        i = bodyEnd + 1
      } else i += 1
    }
    fences.toSeq
  }

  private def runCapturingStdout(src: String): String = {
    val originalOut = System.out
    val buffer = new java.io.ByteArrayOutputStream()
    System.setOut(new java.io.PrintStream(buffer, true, "UTF-8"))
    val result =
      try shell.run(src, "doc-sample.on", Array())
      finally System.setOut(originalOut)
    assert(!result.isInstanceOf[onion.tools.Shell.Failure], s"doc sample failed to compile/run:\n$src")
    buffer.toString("UTF-8")
  }

  describe("docs/guide/null-safety.md") {
    it("documents that == on a nullable operand is null-safe value equality, with a compiling example") {
      val doc = read("docs/guide/null-safety.md")
      val headingLine = doc.linesIterator.find(l => l.trim.startsWith("#") && l.contains("Null-Safe Value Equality"))
      assert(headingLine.isDefined,
        "docs/guide/null-safety.md is missing a section documenting that `==` on a nullable operand " +
        "is null-safe value equality (java.util.Objects.equals semantics) — already documented in " +
        "docs/ja/guide/null-safety.md's \"nullableに対する `==` はnull安全な値等価\" section")

      val section = sectionUnder(doc, headingLine.get.trim)
      assert(section.contains("==="),
        "the null-safe equality section should also mention === for reference identity, like the Japanese guide does")

      val fences = codeFences(section)
      assert(fences.size >= 2, "expected an onion example followed by its Output block under the new section")
      assert(runCapturingStdout(fences(0)).trim == fences(1).trim)
    }
  }
}
