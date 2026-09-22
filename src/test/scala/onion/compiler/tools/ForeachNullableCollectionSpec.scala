package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell

/**
 * `foreach x: T in expr` where `expr` has a nullable type (e.g. `List[Int]?`)
 * crashed the compiler with an uncaught `ClassCastException` (`NullableType`
 * cannot be cast to `ObjectType`) instead of reporting a diagnostic, violating
 * the project's no-crash quality bar. `BlockElementLowering.translate` for
 * `AST.ForeachExpression` only special-cased `isBasicType`/`isNullType`
 * collections before falling into the generic array/map/iterator dispatch,
 * where the iterator branch does `collection.`type`.asInstanceOf[ObjectType]`
 * -- but `NullableType` is not an `ObjectType`, so a nullable, non-array,
 * non-map collection (the common case: `List[Int]?`) crashed that cast.
 * Fixed by special-casing `NullableType` up front and reporting the same
 * null-safety error (E0070) that the equivalent indexing/member-access forms
 * already report, mirroring `ConstructionTyping.typeIndexing`.
 */
class ForeachNullableCollectionSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new java.io.StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  it("rejects a bare foreach over a nullable collection instead of crashing") {
    val result = shell.run(
      """
        |import { java.util.List; java.util.ArrayList; }
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val b: List[Int]? = new ArrayList[Int]()
        |    foreach x: Int in b {
        |    }
        |    return 0
        |  }
        |}
      """.stripMargin,
      "None",
      Array()
    )
    assert(Shell.Failure(-1) == result)
  }

  it("reports E0070, not a crash, for a bare foreach over a nullable collection") {
    val codes = errorCodes(
      """
        |import { java.util.List; java.util.ArrayList; }
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val b: List[Int]? = new ArrayList[Int]()
        |    foreach x: Int in b {
        |    }
        |    return 0
        |  }
        |}
        |""".stripMargin
    )
    assert(codes.contains("E0070"), s"expected E0070 in $codes")
  }

  it("accepts foreach over a nullable collection narrowed by a null check") {
    val result = shell.run(
      """
        |import { java.util.List; java.util.ArrayList; }
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val b: List[Int]? = new ArrayList[Int]()
        |    var total: Int = 0
        |    if b != null {
        |      b.add(1)
        |      b.add(2)
        |      foreach x: Int in b {
        |        total = total + x
        |      }
        |    }
        |    return total
        |  }
        |}
      """.stripMargin,
      "None",
      Array()
    )
    assert(Shell.Success(3) == result)
  }
}
