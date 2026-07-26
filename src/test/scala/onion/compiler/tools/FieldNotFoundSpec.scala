package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * E0004 (FIELD_NOT_FOUND) is reported when a member selection resolves to
 * neither a field, a getter method, nor an array's `length`/`size`, but had
 * no regression coverage at all before this spec.
 */
class FieldNotFoundSpec extends AbstractShellSpec {
  private def errors(src: String): Seq[(Option[String], String)] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(e => (e.errorCode, e.message))
      case _ => Seq.empty
    }
  }

  describe("a member selection with no matching field or getter") {
    it("reports E0004 when reading an undeclared field on a class instance") {
      val results = errors(
        """
          |class Point {
          |public:
          |  val x: Int = 1
          |  val y: Int = 2
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    return new Point().z;
          |  }
          |}
          |""".stripMargin
      )
      assert(results.map(_._1).contains(Some("E0004")))
    }

    it("reports E0004 for an undeclared member on an array (only length/size exist)") {
      val results = errors(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val a: Int[] = new Int[3];
          |    return a.count;
          |  }
          |}
          |""".stripMargin
      )
      assert(results.map(_._1).contains(Some("E0004")))
    }

    it("does not report E0004 when accessing a declared field") {
      val results = errors(
        """
          |class Point {
          |public:
          |  val x: Int = 1
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    return new Point().x;
          |  }
          |}
          |""".stripMargin
      )
      assert(!results.map(_._1).contains(Some("E0004")))
    }

    it("does not report E0004 when accessing an array's length") {
      val results = errors(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val a: Int[] = new Int[3];
          |    return a.length;
          |  }
          |}
          |""".stripMargin
      )
      assert(!results.map(_._1).contains(Some("E0004")))
    }
  }
}
