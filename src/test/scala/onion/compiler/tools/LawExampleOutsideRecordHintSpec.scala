package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * `law`/`example` clauses parse only directly inside a record's own body. Written
 * anywhere else, the found token is just the bare keyword, which used to either
 * fall through to the unrelated "Java-style method" hint (for `law name(...) { ... }`,
 * shaped just like `Type name(...)`) or get no hint at all (for a parameterless
 * `example { ... }`).
 */
class LawExampleOutsideRecordHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("`law`/`example` outside a record body") {
    it("hints at the record-body requirement for `law` in a class, instead of the Java-style-method advice") {
      val msgs = messages(
        """
          |class Foo {
          |public:
          |  law bar(x: Int) { x == x }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("record"), s"expected a hint mentioning `record`, got: $msgs")
      assert(!msgs.contains("public void method"), s"should not fall back to the Java-style-method hint, got: $msgs")
    }

    it("hints at the record-body requirement for `example` in an enum body") {
      val msgs = messages(
        """
          |enum Shape {
          |  case Circle(radius: Double)
          |public:
          |  example { 1 == 1 }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("record"), s"expected a hint mentioning `record`, got: $msgs")
    }

    it("does not fire for a correctly placed `law` inside a record's own body") {
      val msgs = messages(
        """
          |record Point(x: Int, y: Int) {
          |public:
          |  def plus(o: Point): Point = new Point(x + o.x(), y + o.y())
          |  law addComm(a: Point, b: Point) { a.plus(b) == b.plus(a) }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.isEmpty, s"expected no errors for a correctly placed `law`, got: $msgs")
    }
  }
}
