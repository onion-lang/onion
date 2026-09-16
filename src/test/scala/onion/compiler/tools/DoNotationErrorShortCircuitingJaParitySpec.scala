package onion.compiler.tools

/**
 * `docs/guide/control-flow.md`'s "Do Notation (Monadic Composition)" section has a
 * full "### Error Short-Circuiting" subsection (a do-block stops at the first failing
 * bind — real, tested behavior, e.g. `DoNotationMonadsSpec`), but
 * `docs/ja/guide/control-flow.md`'s "## Do記法（モナド合成）" section is only two bare
 * code snippets with no prose at all, so a Japanese-only reader has no way to learn
 * that a do-block short-circuits on failure. This guards that the Japanese guide gains
 * an equivalent subsection, with a compiling example that actually produces the
 * claimed output.
 */
class DoNotationErrorShortCircuitingJaParitySpec extends AbstractShellSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def sectionUnder(doc: String, heading: String): String = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == heading)
    assert(start >= 0, s"could not find heading '$heading' — the scan has rotted")
    val level = heading.trim.takeWhile(_ == '#').length
    val rest = lines.drop(start + 1)
    // Only a heading at the same-or-shallower level ends the section; deeper
    // subheadings (e.g. "###" under a "##" target) stay part of it.
    val end = rest.indexWhere(l => l.trim.startsWith("#") && l.trim.takeWhile(_ == '#').length <= level)
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

  describe("docs/guide/control-flow.md Do Notation section") {
    it("still documents Error Short-Circuiting (sanity check the scan target hasn't moved)") {
      val doc = read("docs/guide/control-flow.md")
      assert(doc.linesIterator.exists(_.trim == "### Error Short-Circuiting"),
        "expected heading '### Error Short-Circuiting' under Do Notation — the scan has rotted")
    }
  }

  describe("docs/ja/guide/control-flow.md Do記法 section") {
    it("documents that a do-block short-circuits on the first failing bind") {
      val section = sectionUnder(read("docs/ja/guide/control-flow.md"), "## Do記法（モナド合成）")
      assert(section.contains("失敗"),
        "docs/ja/guide/control-flow.md's Do記法 section never mentions failure (失敗) at all, " +
        "unlike docs/guide/control-flow.md's 'Error Short-Circuiting' subsection")
      assert(section.contains("打ち切"),
        "docs/ja/guide/control-flow.md's Do記法 section doesn't explain that a do-block " +
        "short-circuits (打ち切られる) on the first failing bind")
    }

    it("the short-circuiting example actually compiles and produces the claimed output") {
      val section = sectionUnder(read("docs/ja/guide/control-flow.md"), "## Do記法（モナド合成）")
      val fences = codeFences(section)
      assert(fences.size >= 3, "expected the introductory onion example plus the short-circuiting " +
        "example and its Output block under docs/ja/guide/control-flow.md's Do記法 section — the scan has rotted")
      val (src, output) = (fences(1), fences(2))
      assert(src.contains("Option::none()"), "expected the short-circuiting example to use Option::none()")
      assert(runCapturingStdout(src).trim == output.trim)
    }
  }
}
