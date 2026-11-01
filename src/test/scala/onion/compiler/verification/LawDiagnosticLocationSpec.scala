package onion.compiler.verification

import onion.compiler.{CompilationOutcome, CompileError, CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Law and example diagnostics used to arrive with `location = null` and the *class name*
 * in the `sourceFile` slot, so they rendered as `Pt:: [E0064] ...` — no file, no line, no
 * caret, and line 0 column 0 in the editor. The clause's `Location` was captured during
 * rewriting and then dropped.
 *
 * The location now survives, via the line-number table the backend already emits.
 */
class LawDiagnosticLocationSpec extends AnyFunSpec {

  private def errors(src: String): Seq[CompileError] = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10)
    new OnionCompiler(config)
      .compile(Seq(new StreamInputSource(() => new StringReader(src), "sample.on"))) match {
      case CompilationOutcome.Failure(es) => es
      case _                              => Seq.empty
    }
  }

  private val main =
    """|class Test {
       |public:
       |  static def main(args: String[]): String { return "ok" }
       |}
       |""".stripMargin

  describe("a falsified law") {
    // `law wrong` is on line 3 of this source.
    val src =
      s"""|
          |record Pt(x: Int, y: Int) {
          |  law wrong(p: Pt) { p.x() == p.y() }
          |}
          |$main""".stripMargin

    it("names the source file the law was written in, not the record") {
      val e = errors(src).find(_.errorCode.contains("E0064")).get
      assert(e.sourceFile == "sample.on", s"got '${e.sourceFile}'")
    }

    it("carries the line the law is written on") {
      val e = errors(src).find(_.errorCode.contains("E0064")).get
      assert(e.location != null, "law diagnostics still have no location")
      assert(e.location.line == 3, s"expected line 3, got ${e.location.line}")
    }
  }

  describe("a failed example") {
    val src =
      s"""|record R(x: Int) {
          |  example { new R(1).x() == 2 }
          |}
          |$main""".stripMargin

    it("carries a location too") {
      val e = errors(src).find(_.errorCode.contains("E0065")).get
      assert(e.sourceFile == "sample.on")
      assert(e.location != null)
      assert(e.location.line == 2, s"expected line 2, got ${e.location.line}")
    }
  }

  describe("a law that cannot be generated") {
    it("carries a location as well") {
      val src =
        s"""|record Dummy(v: Int) {
            |  law overArray(xs: Int[]) { xs != null }
            |}
            |$main""".stripMargin
      val e = errors(src).find(_.errorCode.contains("E0074")).get
      assert(e.sourceFile == "sample.on")
      assert(e.location != null)
      assert(e.location.line == 2, s"expected line 2, got ${e.location.line}")
    }
  }

  describe("the column of a clause inside a record body") {
    it("points at the clause, not column 1 (#2516)") {
      val law = s"""|record Pt(x: Int) {
                    |  law wrong(p: Pt) { p.x() == 0 - 1 }
                    |}
                    |$main""".stripMargin
      val ex = s"""|record R(x: Int) {
                   |  example { new R(1).x() == 2 }
                   |}
                   |$main""".stripMargin
      val l = errors(law).find(_.errorCode.contains("E0064")).get
      val e = errors(ex).find(_.errorCode.contains("E0065")).get
      assert(l.location.column == 7, // under the law's name
         s"law column ${l.location.column}")
      assert(e.location.column > 1, s"example column ${e.location.column}")
    }
  }

  // #2540: the column used to be looked up by line number alone, across every file in the
  // compile, keeping only the first clause on each line.
  describe("the column of a clause in a multi-file or one-line record") {
    def compileAll(files: (String, String)*): Seq[CompileError] = {
      val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10)
      val sources = files.map { case (name, src) => new StreamInputSource(() => new StringReader(src), name) }
      new OnionCompiler(config).compile(sources) match {
        case CompilationOutcome.Failure(es) => es
        case _                              => Seq.empty
      }
    }

    val a = """|record A(x: Int) {
               |
               |    law       aaa(a: A) { a.x() == 7 }
               |}
               |""".stripMargin
    val b = s"""|record B(x: Int) {
                |  //
                |  example { 1 > 2 }
                |}
                |$main""".stripMargin

    it("takes the column from the clause's own file, not another file's clause on that line") {
      val es = compileAll("A.on" -> a, "B.on" -> b)
      val law = es.find(e => e.errorCode.contains("E0064") && e.sourceFile == "A.on")
        .getOrElse(fail(s"no E0064 in A.on: ${es.map(e => (e.sourceFile, e.errorCode))}"))
      val ex = es.find(e => e.errorCode.contains("E0065") && e.sourceFile == "B.on")
        .getOrElse(fail(s"no E0065 in B.on: ${es.map(e => (e.sourceFile, e.errorCode))}"))
      assert((law.location.line, law.location.column) == (3, 15), s"law at ${law.location.line}:${law.location.column}")
      assert((ex.location.line, ex.location.column) == (3, 3), s"example at ${ex.location.line}:${ex.location.column}")
    }

    it("gives each clause on one line its own column") {
      val line = "record C(x: Int) { example e1 { 1 > 2 } law l(c: C) { c.x() < 100 } example e2 { 3 > 4 } }"
      val es = errors(line + "\n" + main)
      // An example is reported at its keyword, a law at its name (as for one clause per line).
      def col(text: String): Int = line.indexOf(text) + 1
      def columnOf(code: String, label: String): Int =
        es.find(e => e.errorCode.contains(code) && e.message.contains(label))
          .getOrElse(fail(s"no $code naming $label: ${es.map(_.message)}")).location.column
      assert(columnOf("E0065", "`e1`") == col("example e1"))
      assert(columnOf("E0064", "`l`") == col("l(c: C)"))
      assert(columnOf("E0065", "`e2`") == col("example e2"))
    }
  }
}
