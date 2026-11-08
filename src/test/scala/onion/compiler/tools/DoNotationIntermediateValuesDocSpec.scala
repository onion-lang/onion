package onion.compiler.tools

/**
 * #2668: the guide's longer do-block example ("Mixing with Local Declarations and Side
 * Effects") used a `val` and a bare `println`, neither of which a do block accepts, so a
 * newcomer copying it got two errors. The section now shows the forms that compile (bind an
 * intermediate value, run a side effect in the `yield` block). This keeps the en and ja
 * samples compiling and printing what their Output blocks claim.
 */
class DoNotationIntermediateValuesDocSpec extends AbstractShellSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def sectionUnder(doc: String, heading: String): String = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == heading)
    assert(start >= 0, s"could not find heading '$heading'")
    val level = heading.trim.takeWhile(_ == '#').length
    val rest = lines.drop(start + 1)
    val end = rest.indexWhere(l => l.trim.startsWith("#") && l.trim.takeWhile(_ == '#').length <= level)
    (if (end >= 0) rest.take(end) else rest).mkString("\n")
  }

  private def codeFences(section: String): Seq[String] = {
    val lines = section.linesIterator.toSeq
    val fences = scala.collection.mutable.ArrayBuffer[String]()
    var i = 0
    while (i < lines.length) {
      if (lines(i).trim.startsWith("```")) {
        val bodyEnd = lines.indexWhere(_.trim.startsWith("```"), i + 1)
        fences += lines.slice(i + 1, bodyEnd).mkString("\n")
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

  private def checkSample(path: String, heading: String): Unit = {
    val fences = codeFences(sectionUnder(read(path), heading))
    assert(fences.size >= 2, s"expected an example and its Output block under '$heading' in $path")
    val out = runCapturingStdout(fences(0))
    assert(out.linesIterator.map(_.trim).toSeq == fences(1).linesIterator.map(_.trim).toSeq)
  }

  describe("the do-block intermediate values and side effects example (#2668)") {
    it("compiles and prints its Output block in docs/guide/control-flow.md") {
      checkSample("docs/guide/control-flow.md", "### Intermediate Values and Side Effects")
    }
    it("compiles and prints its Output block in docs/ja/guide/control-flow.md") {
      checkSample("docs/ja/guide/control-flow.md", "### 途中の値と副作用")
    }
  }
}
