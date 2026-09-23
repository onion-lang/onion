package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * `select scrutinee { ... }` where `scrutinee: T?` (a nullable value, never
 * null-checked) either reported the misleading `E0020` ("this method cannot
 * return a value") in expression position, or compiled with **no diagnostic
 * at all** in statement position -- a genuine silent miscompile: every case's
 * `instanceof`-style check fails against a null scrutinee, so the whole
 * select body silently does nothing at runtime, instead of the null-safety
 * error (`E0070`, `NULLABLE_MEMBER_ACCESS`) that every other dereference-like
 * use of a nullable value (member access, indexing, operators, conditions,
 * `foreach`, destructuring, array size, `throw`, try-with-resources) already
 * reports.
 *
 * `SelectExpressionTyping.typeSelectExpression` never checked the typed
 * scrutinee for `NullableType` up front: `conditionClass` (used to look up
 * a sealed/enum hierarchy for the exhaustiveness check) unwrapped
 * `AppliedClassType` but not `NullableType`, so a nullable scrutinee's raw
 * class never matched `classDef.isSealed`/`isEnum` and the exhaustiveness
 * check -- and with it, any diagnostic -- was silently skipped. Fixed by
 * reporting `NULLABLE_MEMBER_ACCESS` for a `NullableType` scrutinee up
 * front, mirroring the equivalent `throw`/`foreach`/condition checks.
 */
class NullableSelectScrutineeSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  private val shapeEnum =
    """
      |enum Shape {
      |  case Circle(radius: Double)
      |  case Square(side: Double)
      |}
      |""".stripMargin

  it("reports E0070, not the misleading E0020, for a nullable scrutinee in expression position") {
    val codes = errorCodes(
      shapeEnum +
        """
          |class Test {
          |public:
          |  static def describe(s: Shape?): String = select s {
          |    case c is Circle: "circle"
          |    case sq is Square: "square"
          |  }
          |  static def main(args: String[]): Int {
          |    IO::println(describe(new Circle(1.0)))
          |    return 0
          |  }
          |}
          |""".stripMargin
    )
    assert(codes.contains("E0070"), s"expected E0070 in $codes")
    assert(!codes.contains("E0020"), s"must not fall back to the misleading E0020 message: $codes")
  }

  it("reports E0070 instead of silently compiling with no diagnostic in statement position") {
    val codes = errorCodes(
      shapeEnum +
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val s: Shape? = null
          |    select s {
          |      case c is Circle: IO::println("circle")
          |      case sq is Square: IO::println("square")
          |    }
          |    IO::println("after select")
          |    return 0
          |  }
          |}
          |""".stripMargin
    )
    assert(codes.contains("E0070"), s"expected E0070, got no diagnostic at all (silent miscompile): $codes")
  }

  it("still compiles a genuinely non-nullable, exhaustive select with no errors") {
    val codes = errorCodes(
      shapeEnum +
        """
          |class Test {
          |public:
          |  static def describe(s: Shape): String = select s {
          |    case c is Circle: "circle"
          |    case sq is Square: "square"
          |  }
          |  static def main(args: String[]): Int {
          |    IO::println(describe(new Circle(1.0)))
          |    return 0
          |  }
          |}
          |""".stripMargin
    )
    assert(codes.isEmpty, s"expected no errors for a non-nullable exhaustive select: $codes")
  }

  it("still compiles a nullable scrutinee once asserted non-null with !!") {
    val codes = errorCodes(
      shapeEnum +
        """
          |class Test {
          |public:
          |  static def describe(s: Shape?): String = select s!! {
          |    case c is Circle: "circle"
          |    case sq is Square: "square"
          |  }
          |  static def main(args: String[]): Int {
          |    IO::println(describe(new Circle(1.0)))
          |    return 0
          |  }
          |}
          |""".stripMargin
    )
    assert(codes.isEmpty, s"expected no errors after `!!`-asserting non-null: $codes")
  }
}
