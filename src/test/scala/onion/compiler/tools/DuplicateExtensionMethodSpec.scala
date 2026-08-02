package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * Two methods with the same name and parameter types declared in the same
 * `extension` block went completely unchecked: the container class (e.g.
 * `Extension$Double`) compiled with two same-signature static methods, which
 * only failed later as a JVM `ClassFormatError` ("Duplicate method name ...")
 * once something loaded the class — surfaced as an internal compiler error
 * (I0000) instead of a normal diagnostic. Caught by the mutation fuzzer
 * duplicating a method line in `run/TaskPlanner.on`'s `extension Double`
 * block; the duplicated method (`pct`) was never called, so the ambiguous-call
 * check (E0006) that catches a *used* duplicate never ran either.
 */
class DuplicateExtensionMethodSpec extends AbstractShellSpec {
  private def errors(src: String): Seq[(Option[String], String)] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(e => (e.errorCode, e.message))
      case _ => Seq.empty
    }
  }

  describe("duplicate methods within the same extension block") {
    it("reports E0084 even when the duplicated method is never called") {
      val results = errors(
        """
          |extension Double {
          |  def pct(): String = "" + self
          |  def pct(): String = "" + self
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "ok" }
          |}
          |""".stripMargin
      )
      val codes = results.map(_._1)
      assert(codes.contains(Some("E0084")))
      val message = results.collectFirst { case (Some("E0084"), msg) => msg }.get
      assert(message.contains("pct"))
      assert(message.contains("Double"))
    }

    it("still allows two extension methods with the same name but different parameter types") {
      val results = errors(
        """
          |extension Double {
          |  def fmt(): String = "" + self
          |  def fmt(prefix: String): String = prefix + self
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "ok" }
          |}
          |""".stripMargin
      )
      assert(!results.map(_._1).contains(Some("E0084")))
    }

    it("still allows extension methods with the same name on different receiver types") {
      val results = errors(
        """
          |extension Double {
          |  def label(): String = "" + self
          |}
          |extension Int {
          |  def label(): String = "" + self
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "ok" }
          |}
          |""".stripMargin
      )
      assert(!results.map(_._1).contains(Some("E0084")))
    }
  }
}
