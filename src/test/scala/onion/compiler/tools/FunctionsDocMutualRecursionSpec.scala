package onion.compiler.tools

import onion.tools.Shell

/**
 * `docs/guide/functions.md`'s "Tail Recursion" > "Limitations" bullet flatly claimed
 * "Only direct self-recursion is optimized (not mutual recursion)" with no mention that
 * an opt-in path exists — but `@TailRecursive` + `MutualRecursionOptimization`
 * (see MutualRecursionOptimizationSpec / IneffectiveTailRecursiveWarningSpec) is a real,
 * tested feature, documented only in docs/compiler/tail-call-optimization.md and never
 * surfaced in the user-facing guide. `docs/ja/guide/functions.md` didn't even have a
 * "Limitations"/mutual-recursion section at all. This guards that both guides now mention
 * `@TailRecursive` for mutual recursion, and that the added onion example actually compiles
 * and produces the claimed result.
 */
class FunctionsDocMutualRecursionSpec extends AbstractShellSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def codeFences(doc: String): Seq[String] = {
    val lines = doc.linesIterator.toSeq
    val fences = scala.collection.mutable.ArrayBuffer[String]()
    var i = 0
    while (i < lines.length) {
      if (lines(i).trim.startsWith("```onion")) {
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

  describe("docs/guide/functions.md Tail Recursion Limitations") {
    it("mentions @TailRecursive as the opt-in path for mutual recursion, not just 'not optimized'") {
      val doc = read("docs/guide/functions.md")
      val limitationsIdx = doc.indexOf("**Limitations:**")
      assert(limitationsIdx >= 0, "could not find the Limitations bullet — the scan has rotted")
      val mutualRecursionIdx = doc.indexOf("mutual recursion", limitationsIdx)
      assert(mutualRecursionIdx >= 0, "Limitations no longer mentions mutual recursion at all")
      assert(doc.indexOf("@TailRecursive", limitationsIdx) >= 0,
        "docs/guide/functions.md discusses mutual recursion but never mentions the @TailRecursive opt-in")
    }

    it("the Mutual Recursion example actually compiles and prints the claimed result") {
      val fences = codeFences(doc = {
        val doc = read("docs/guide/functions.md")
        val start = doc.indexOf("### Mutual Recursion")
        assert(start >= 0, "expected a 'Mutual Recursion' subsection under Tail Recursion — the scan has rotted")
        val end = doc.indexOf("## Method Overloading", start)
        doc.substring(start, if (end >= 0) end else doc.length)
      })
      assert(fences.nonEmpty, "expected an onion example under 'Mutual Recursion'")
      assert(fences(0).contains("@TailRecursive"), "example under 'Mutual Recursion' doesn't use @TailRecursive")
      assert(runCapturingStdout(fences(0)).trim == "true")
    }
  }

  describe("docs/ja/guide/functions.md mutual recursion coverage") {
    it("mentions @TailRecursive for mutual recursion, matching the English guide") {
      val doc = read("docs/ja/guide/functions.md")
      assert(doc.contains("相互再帰"), "docs/ja/guide/functions.md doesn't mention mutual recursion (相互再帰) at all")
      assert(doc.contains("@TailRecursive"),
        "docs/ja/guide/functions.md mentions mutual recursion but not the @TailRecursive opt-in")
    }
  }
}
