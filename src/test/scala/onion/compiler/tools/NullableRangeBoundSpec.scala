package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * A nullable operand (e.g. `n: Int?`) used as a range bound in `a..b`/`a..<b`
 * reported the generic "a constructor applicable for Range(Int?, Int, Boolean)
 * is not found" (E0021) instead of the null-safety error (E0070) that every
 * other dereference-like use of a nullable value (member access, indexing,
 * operators, conditions, foreach, array size, throw, try-with-resources,
 * destructuring) already reports.
 *
 * The parser desugars `a..b`/`a..<b` into `new onion.Range(a, b, inclusive)`
 * (`OnionParser.rangeNew`/the JavaCC grammar's equivalent production), so a
 * nullable bound never dereferences anything directly -- it just fails
 * `ConstructionTyping.typeNewObject`'s ordinary constructor-overload
 * resolution, which reports the generic constructor-not-found error and
 * leaks the "Range" implementation-detail class name the user never wrote.
 * Fixed by special-casing a `NullableType` start/end argument for the
 * `onion.Range` constructor up front, before overload resolution, mirroring
 * `typeNewArray`'s nullable-array-size check right above it.
 */
class NullableRangeBoundSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  private def program(expr: String): String =
    s"""
       |class Test {
       |public:
       |  static def main(args: String[]): Int {
       |    val n: Int? = null
       |    foreach i: Int in $expr {
       |      IO::println(i)
       |    }
       |    return 0
       |  }
       |}
       |""".stripMargin

  it("reports E0070, not the generic E0021 fallback, for a nullable start bound in `n..10`") {
    val codes = errorCodes(program("n..10"))
    assert(codes.contains("E0070"), s"expected E0070 in $codes")
    assert(!codes.contains("E0021"), s"must not fall back to the generic E0021 message: $codes")
  }

  it("reports E0070 for a nullable end bound in `1..n`") {
    val codes = errorCodes(program("1..n"))
    assert(codes.contains("E0070"), s"expected E0070 in $codes")
    assert(!codes.contains("E0021"), s"must not fall back to the generic E0021 message: $codes")
  }

  it("reports E0070 for a nullable start bound in the exclusive form `n..<10`") {
    val codes = errorCodes(program("n..<10"))
    assert(codes.contains("E0070"), s"expected E0070 in $codes")
    assert(!codes.contains("E0021"), s"must not fall back to the generic E0021 message: $codes")
  }

  it("reports E0070 for a nullable end bound in the exclusive form `1..<n`") {
    val codes = errorCodes(program("1..<n"))
    assert(codes.contains("E0070"), s"expected E0070 in $codes")
    assert(!codes.contains("E0021"), s"must not fall back to the generic E0021 message: $codes")
  }

  it("rejects a nullable range bound at runtime-failure granularity via Shell.Failure(-1)") {
    assert(Shell.Failure(-1) == shell.run(program("n..10"), "None", Array()))
  }

  it("still compiles a genuinely non-nullable range with no E0070") {
    val codes = errorCodes(
      """
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val a: Int = 1
        |    val b: Int = 10
        |    foreach i: Int in a..b {
        |      IO::println(i)
        |    }
        |    return 0
        |  }
        |}
        |""".stripMargin
    )
    assert(!codes.contains("E0070"), s"must not claim nullability for a non-nullable range: $codes")
  }
}
