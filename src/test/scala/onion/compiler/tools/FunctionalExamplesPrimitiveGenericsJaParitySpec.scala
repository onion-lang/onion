package onion.compiler.tools

import onion.tools.Shell

/**
 * `docs/examples/functional.md`'s "## Primitive Generics with Java Functional Interfaces"
 * section (boxing Int type arguments when converting lambdas to Comparator/Predicate/etc.)
 * was entirely missing from `docs/ja/examples/functional.md` — the Japanese file jumped
 * straight from "## 末尾ラムダ構文" ("Trailing Lambda Syntax") to "## 次のステップ"
 * ("Next Steps"), so a Japanese-only reader had no way to learn this behavior exists.
 * Guards that the section was added and that its onion sample actually compiles and runs.
 */
class FunctionalExamplesPrimitiveGenericsJaParitySpec extends AbstractShellSpec {

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

  describe("docs/ja/examples/functional.md Primitive Generics section") {
    it("has a translated section between Trailing Lambda Syntax and Next Steps") {
      val doc = read("docs/ja/examples/functional.md")
      val trailingIdx = doc.indexOf("## 末尾ラムダ構文")
      assert(trailingIdx >= 0, "could not find the Trailing Lambda Syntax heading — the scan has rotted")
      val nextStepsIdx = doc.indexOf("## 次のステップ", trailingIdx)
      assert(nextStepsIdx >= 0, "could not find the Next Steps heading")
      val between = doc.substring(trailingIdx, nextStepsIdx)
      assert(between.contains("プリミティブ"),
        "docs/ja/examples/functional.md has no Primitive Generics section between Trailing Lambda Syntax and Next Steps")
      assert(between.contains("Comparator") || between.contains("Predicate"),
        "the Primitive Generics section should reference the Java functional interfaces it discusses")
    }

    it("the added onion sample actually compiles and runs, matching the English doc's sample") {
      val enDoc = read("docs/examples/functional.md")
      val enSectionIdx = enDoc.indexOf("## Primitive Generics with Java Functional Interfaces")
      assert(enSectionIdx >= 0, "English doc lost its Primitive Generics section")
      val enNextStepsIdx = enDoc.indexOf("## Next Steps", enSectionIdx)
      val enFence = codeFences(enDoc.substring(enSectionIdx, enNextStepsIdx)).head

      val jaDoc = read("docs/ja/examples/functional.md")
      val trailingIdx = jaDoc.indexOf("## 末尾ラムダ構文")
      val nextStepsIdx = jaDoc.indexOf("## 次のステップ", trailingIdx)
      val jaFences = codeFences(jaDoc.substring(trailingIdx, nextStepsIdx))
      assert(jaFences.nonEmpty, "no onion code sample found in the ja Primitive Generics section")
      val jaFence = jaFences.last

      val enOutput = runCapturingStdout(enFence)
      val jaOutput = runCapturingStdout(jaFence)
      assert(jaOutput == enOutput, "ja sample output should match the English sample's output")
    }
  }
}
