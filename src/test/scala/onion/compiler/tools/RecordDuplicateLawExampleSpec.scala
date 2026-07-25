package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * Two `law`/`example` clauses on the same record that mangle to the same synthesized
 * method name+signature (e.g. two `law roundtrip(p: Point) { ... }` clauses) used to
 * pass typing unchecked — `TypingDuplicationPass.processRecordDeclaration` never walked
 * a record's `sections`/`synthesizedMethods` the way it does for a class — and only blew
 * up later as a JVM `ClassFormatError` ("Duplicate method name ... in class file ..."),
 * surfaced as an internal compiler error (I0000) rather than a normal compile error.
 * Found by the mutation fuzzer duplicating a `law` line in run/RecordLaws.on.
 */
class RecordDuplicateLawExampleSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  describe("duplicate law/example clauses on a record") {
    it("reports E0026 (not an internal compiler error) for two laws with the same name and params") {
      val codes = errorCodes(
        """
          |record Point(x: Int, y: Int) from re"(-?\d+),(-?\d+)"
          |  law roundtrip(p: Point) { Point::parse(Point::format(p)) == p }
          |  law roundtrip(p: Point) { Point::parse(Point::format(p)) == p }
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "ok" }
          |}
          |""".stripMargin
      )
      assert(codes.contains("E0026"))
      assert(!codes.contains("I0000"))
    }

    it("reports E0026 for two examples with the same explicit name") {
      val codes = errorCodes(
        """
          |record R(x: Int)
          |  example dup { new R(1).x() == 1 }
          |  example dup { new R(2).x() == 2 }
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "ok" }
          |}
          |""".stripMargin
      )
      assert(codes.contains("E0026"))
      assert(!codes.contains("I0000"))
    }

    it("still allows two laws with the same name but different parameter types") {
      val codes = errorCodes(
        """
          |record Point(x: Int, y: Int)
          |  law reflexiveP(p: Point) { p == p }
          |  law reflexiveN(n: Int) { n == n }
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "ok" }
          |}
          |""".stripMargin
      )
      assert(codes.isEmpty)
    }
  }
}
