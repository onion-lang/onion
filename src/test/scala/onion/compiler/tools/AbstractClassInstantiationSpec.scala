package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * E0038 (ABSTRACT_CLASS_INSTANTIATION) is reported when `new` targets a class
 * declared `abstract`, but had no regression coverage at all before this spec.
 */
class AbstractClassInstantiationSpec extends AbstractShellSpec {
  private def errors(src: String): Seq[(Option[String], String)] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(e => (e.errorCode, e.message))
      case _ => Seq.empty
    }
  }

  describe("instantiating an abstract class") {
    it("reports E0038 when directly instantiating an abstract class") {
      val results = errors(
        """
          |abstract class Shape {
          |public:
          |  abstract def area(): Int;
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val s: Shape = new Shape();
          |    return 0;
          |  }
          |}
          |""".stripMargin
      )
      assert(results.map(_._1).contains(Some("E0038")))
    }

    it("reports E0038 when instantiating an abstract class with no abstract methods of its own") {
      val results = errors(
        """
          |abstract class Base {
          |public:
          |  def helper(): Int { return 1; }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val b: Base = new Base();
          |    return 0;
          |  }
          |}
          |""".stripMargin
      )
      assert(results.map(_._1).contains(Some("E0038")))
    }

    it("does not report E0038 when instantiating a concrete subclass of an abstract class") {
      val results = errors(
        """
          |abstract class Shape {
          |public:
          |  abstract def area(): Int;
          |}
          |class Square extends Shape {
          |  var side: Int;
          |public:
          |  def this(s: Int) { this.side = s; }
          |  def area(): Int { return this.side * this.side; }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val s: Shape = new Square(5);
          |    return s.area();
          |  }
          |}
          |""".stripMargin
      )
      assert(!results.map(_._1).contains(Some("E0038")))
    }

    it("does not report E0038 for a normal, non-abstract class") {
      val results = errors(
        """
          |class Plain {
          |public:
          |  def value(): Int { return 42; }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    return new Plain().value();
          |  }
          |}
          |""".stripMargin
      )
      assert(!results.map(_._1).contains(Some("E0038")))
    }
  }
}
