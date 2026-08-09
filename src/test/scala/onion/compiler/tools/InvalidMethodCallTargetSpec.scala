package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * E0041 (INVALID_METHOD_CALL_TARGET) is reported from several sites in
 * MethodTargetTypingSupport/ConstructionTyping/AssignmentTyping, but had no
 * regression coverage at all before this spec.
 */
class InvalidMethodCallTargetSpec extends AbstractShellSpec {
  private def errors(src: String): Seq[(Option[String], String)] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(e => (e.errorCode, e.message))
      case _ => Seq.empty
    }
  }

  describe("indexing a value whose type isn't a usable receiver") {
    it("reports E0041 when indexing a nullable class-typed value") {
      val results = errors(
        """
          |class Box {
          |public:
          |  def this {}
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val b: Box? = null;
          |    val v = b[0];
          |    return 0;
          |  }
          |}
          |""".stripMargin
      )
      assert(results.map(_._1).contains(Some("E0041")))
    }

    it("does not report E0041 when indexing a non-null class-typed value with a get method") {
      val results = errors(
        """
          |class Box {
          |public:
          |  def this {}
          |  def get(i: Int): Int = i
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val b: Box = new Box();
          |    return b[0];
          |  }
          |}
          |""".stripMargin
      )
      assert(!results.map(_._1).contains(Some("E0041")))
    }
  }
}
