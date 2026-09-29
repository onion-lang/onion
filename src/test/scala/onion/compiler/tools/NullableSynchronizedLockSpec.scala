package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * A nullable lock (`synchronized (n) { }` where `n: String?`, never
 * null-checked) compiled cleanly instead of reporting the null-safety error
 * (E0070, NULLABLE_MEMBER_ACCESS) that every other dereference-like use of a
 * nullable value (member access, indexing, operators, conditions, array
 * size, foreach, destructuring, throw, try-with-resources, range bounds,
 * select scrutinee, assignment, catch type) already reports -- letting the
 * lock silently reach `monitorenter` at run time and fail only with an NPE,
 * instead of being caught at compile time like every sibling nullable use.
 *
 * Both `synchronized` code paths -- the expression form
 * (`TryExpressionTyping.typeSynchronizedExpression`, used when the block's
 * value is consumed) and the statement form
 * (`BlockElementLowering.translate`'s `AST.SynchronizedExpression` case,
 * used when it isn't) -- only checked `lock.isBasicType` to reject a
 * primitive lock, and `NullableType` is not a `BasicType`, so a nullable
 * object-typed lock fell through that guard entirely in both. Fixed by
 * special-casing a `NullableType` lock up front in both paths, mirroring
 * every other nullable-dereference fix.
 */
class NullableSynchronizedLockSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  private def statementProgram(lockType: String): String =
    s"""
       |class Test {
       |public:
       |  static def main(args: String[]): Int {
       |    val lock: $lockType = null
       |    synchronized (lock) {
       |      IO::println("x")
       |    }
       |    return 0
       |  }
       |}
       |""".stripMargin

  private def expressionProgram(lockType: String): String =
    s"""
       |class Test {
       |public:
       |  static def main(args: String[]): Int {
       |    val lock: $lockType = null
       |    val x: Int = synchronized (lock) { 1 }
       |    return x
       |  }
       |}
       |""".stripMargin

  it("reports E0070, not a silent compile, for a nullable synchronized lock (statement position)") {
    val codes = errorCodes(statementProgram("String?"))
    assert(codes.contains("E0070"), s"expected E0070 in $codes")
  }

  it("reports E0070, not a silent compile, for a nullable synchronized lock (expression position)") {
    val codes = errorCodes(expressionProgram("String?"))
    assert(codes.contains("E0070"), s"expected E0070 in $codes")
  }

  it("rejects a nullable synchronized lock at runtime-failure granularity via Shell.Failure(-1)") {
    assert(Shell.Failure(-1) == shell.run(statementProgram("String?"), "None", Array()))
  }

  it("still compiles cleanly for a genuinely non-nullable lock (statement position)") {
    val codes = errorCodes(statementProgram("String").replace("val lock: String = null", "val lock: String = \"x\""))
    assert(codes.isEmpty, s"expected no errors for a non-nullable lock: $codes")
  }

  it("still compiles cleanly for a genuinely non-nullable lock (expression position)") {
    val codes = errorCodes(expressionProgram("String").replace("val lock: String = null", "val lock: String = \"x\""))
    assert(codes.isEmpty, s"expected no errors for a non-nullable lock: $codes")
  }

  it("still reports the generic E0000 for a primitive, non-object lock (statement position)") {
    val codes = errorCodes(statementProgram("Int").replace("val lock: Int = null", "val lock: Int = 1"))
    assert(codes.contains("E0000"), s"expected E0000 for a primitive lock: $codes")
    assert(!codes.contains("E0070"), s"must not claim nullability for a primitive lock: $codes")
  }
}
