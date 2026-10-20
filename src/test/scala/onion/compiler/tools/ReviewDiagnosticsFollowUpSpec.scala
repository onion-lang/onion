package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome, CompileError}
import java.io.StringReader

/**
 * Follow-ups from a review of #2266/#2272/#2273:
 *  - E0101's one-report-per-lambda guard keyed on line/column only, so a second file
 *    with a mistaken lambda at the same position lost its E0101.
 *  - `List + List` with different element types added a spurious E0000 to the E0001,
 *    though the operator-extension path is meant to report nothing on a miss.
 *  - The sibling-method hint could name a private method the caller cannot use.
 */
class ReviewDiagnosticsFollowUpSpec extends AbstractShellSpec {
  private def compile(files: (String, String)*): Seq[CompileError] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val inputs = files.map { case (name, src) => new StreamInputSource(() => new StringReader(src), name) }
    new OnionCompiler(config).compile(inputs) match {
      case CompilationOutcome.Failure(errs) => errs
      case _ => Seq.empty
    }
  }

  describe("E0101 across files") {
    it("reports the same mistake at the same position in two files, once each") {
      val src = "val m = [\"a\": 1]\nprintln(m.filter { e -> true })\n"
      val errs = compile("A.on" -> src, "B.on" -> src.replace("m", "n"))
      val e0101Files = errs.filter(_.code.contains("E0101")).map(_.sourceFile).toSet
      assert(e0101Files == Set("A.on", "B.on"), errs.map(e => (e.sourceFile, e.code, e.message)))
    }
  }

  describe("List + List with different element types") {
    it("reports only the operator error, not an extra E0000") {
      val errs = compile("t.on" -> "val ss: List[String] = [\"a\"]\nprintln([1, 2] + ss)\n")
      val codes = errs.flatMap(_.code)
      assert(codes.contains("E0001") && !codes.contains("E0000"), errs.map(e => (e.code, e.message)))
    }
  }

  describe("the sibling-method hint") {
    it("does not name a private method") {
      val errs = compile("t.on" ->
        """class A {
          |public:
          |  def this {}
          |  def foo(x: Int): Int = x
          |private:
          |  def fooSecret(s: String): Int = 0
          |}
          |println(new A().foo("s"))
          |""".stripMargin)
      val msgs = errs.map(_.message).mkString("\n")
      assert(errs.flatMap(_.code).contains("E0005") && !msgs.contains("fooSecret"), msgs)
    }
  }
}
