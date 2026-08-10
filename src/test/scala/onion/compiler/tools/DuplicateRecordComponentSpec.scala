package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A record with two components sharing the same name (`record R(a: Int, a: Int)`)
 * generates two same-named private fields and two same-named public accessor
 * methods, which went completely unchecked at typing time: the record compiled
 * clean and only failed later as a JVM `ClassFormatError` ("Duplicate method
 * name ...") once something loaded the class, surfaced as an internal compiler
 * error (I0000) instead of a normal diagnostic. Found by the mutation fuzzer
 * duplicating a component line in `run/TextAnalytics.on` (issue #666).
 */
class DuplicateRecordComponentSpec extends AbstractShellSpec {
  private def errors(src: String): Seq[(Option[String], String)] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(e => (e.errorCode, e.message))
      case _ => Seq.empty
    }
  }

  describe("duplicate component names within the same record") {
    it("reports E0086 instead of crashing the compiler with I0000") {
      val results = errors(
        """
          |record R(a: Int, a: Int)
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "ok" }
          |}
          |""".stripMargin
      )
      val codes = results.map(_._1)
      assert(codes.contains(Some("E0086")))
      val message = results.collectFirst { case (Some("E0086"), msg) => msg }.get
      assert(message.contains("a"))
      assert(message.contains("R"))
    }

    it("still allows records whose component names are all distinct") {
      val results = errors(
        """
          |record R(a: Int, b: Int)
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "ok" }
          |}
          |""".stripMargin
      )
      assert(!results.map(_._1).contains(Some("E0086")))
    }

    it("reports one error per extra duplicate when a component name repeats three times") {
      val results = errors(
        """
          |record R(a: Int, a: Int, a: Int)
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "ok" }
          |}
          |""".stripMargin
      )
      val codes = results.map(_._1)
      assert(codes.count(_ == Some("E0086")) == 2)
    }
  }
}
