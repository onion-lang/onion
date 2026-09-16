package onion.compiler.tools

import onion.tools.Shell

/**
 * Labeled break/continue (`outer: while ...`, `break outer`, `continue outer`) is a
 * real, tested language feature — it even has its own diagnostic (E0058, see
 * LabelNotFoundSpec) for an unbound label — but docs/guide/control-flow.md never
 * documented it at all, and docs/ja/guide/control-flow.md claimed "ラベル付きの
 * break / continue も使えます" while showing an example with no label whatsoever.
 * This guards both: the English guide must show a labeled-loop example that
 * actually compiles and produces the output it claims, and the Japanese guide's
 * break/continue example must actually contain a label.
 */
class ControlFlowDocLabeledLoopSpec extends AbstractShellSpec {

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
    assert(!result.isInstanceOf[Shell.Failure], s"doc sample failed to compile/run:\n$src")
    buffer.toString("UTF-8")
  }

  describe("docs/guide/control-flow.md Labeled Break and Continue section") {
    it("labeled break example compiles and produces the documented output") {
      val fences = codeFences(sectionUnder(read("docs/guide/control-flow.md"), "### Labeled Break and Continue"))
      assert(fences.size >= 2, "expected a labeled-break onion example followed by its Output block — the scan has rotted")
      assert(fences(0).contains("break "), "first example under 'Labeled Break and Continue' is not a break example")
      assert(runCapturingStdout(fences(0)).trim == fences(1).trim)
    }

    it("labeled continue example compiles and produces the documented output") {
      val fences = codeFences(sectionUnder(read("docs/guide/control-flow.md"), "### Labeled Break and Continue"))
      assert(fences.size >= 4, "expected a second, labeled-continue onion example followed by its Output block — the scan has rotted")
      assert(fences(2).contains("continue "), "second example under 'Labeled Break and Continue' is not a continue example")
      assert(runCapturingStdout(fences(2)).trim == fences(3).trim)
    }
  }

  describe("docs/ja/guide/control-flow.md break / continue section") {
    it("shows an actual labeled loop, not just prose claiming label support") {
      val section = sectionUnder(read("docs/ja/guide/control-flow.md"), "## break / continue")
      val labelPattern = """\b\w+:\s*(while|for|foreach|do)\b""".r
      assert(labelPattern.findFirstIn(section).isDefined,
        "docs/ja/guide/control-flow.md's break/continue section claims labeled break/continue " +
        "but its example never shows a label (`name: while ...`) — the reader can't see the syntax")
    }

    it("documents that a labeled break/continue unwinds through synchronized/try-with-resources, like the English guide does") {
      val section = sectionUnder(read("docs/ja/guide/control-flow.md"), "## break / continue")
      assert(section.contains("synchronized") && section.contains("try"),
        "docs/ja/guide/control-flow.md's break/continue section is missing the note (present in " +
        "docs/guide/control-flow.md's 'Labeled Break and Continue' section) that a labeled break/continue " +
        "correctly unwinds through synchronized blocks and try (including try-with-resources) between it " +
        "and the labeled loop")
    }
  }
}
