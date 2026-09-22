package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * Indexing a nullable value directly (`b[i]` where `b: List[Int]?`, or the write
 * form `b[i] = v`) reported the generic "not a valid method call target" (E0041)
 * instead of the null-safety error (E0070) that the equivalent member access
 * (`b.field` / `b.field = v`) already reports -- matching neither the read path
 * (`ConstructionTyping.typeIndexing`) nor the write path
 * (`AssignmentTyping.processArrayAssign`), both of which never special-cased a
 * `NullableType` target before falling into the generic case.
 */
class NullableIndexingAccessSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  it("rejects direct indexing read on a nullable value") {
    val result = shell.run(
      """
        |import { java.util.List; java.util.ArrayList; }
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val b: List[Int]? = new ArrayList[Int]()
        |    return b[0]
        |  }
        |}
      """.stripMargin,
      "None",
      Array()
    )
    assert(Shell.Failure(-1) == result)
  }

  it("reports E0070, not the generic E0041 fallback, for direct indexing read on a nullable value") {
    val codes = errorCodes(
      """
        |import { java.util.List; java.util.ArrayList; }
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val b: List[Int]? = new ArrayList[Int]()
        |    return b[0]
        |  }
        |}
        |""".stripMargin
    )
    assert(codes.contains("E0070"), s"expected E0070 in $codes")
    assert(!codes.contains("E0041"), s"must not fall back to the generic E0041 message: $codes")
  }

  it("rejects direct indexing assignment on a nullable value") {
    val result = shell.run(
      """
        |import { java.util.List; java.util.ArrayList; }
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val b: List[Int]? = new ArrayList[Int]()
        |    b[0] = 5
        |    return 0
        |  }
        |}
      """.stripMargin,
      "None",
      Array()
    )
    assert(Shell.Failure(-1) == result)
  }

  it("reports E0070, not the generic E0041 fallback, for direct indexing assignment on a nullable value") {
    val codes = errorCodes(
      """
        |import { java.util.List; java.util.ArrayList; }
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val b: List[Int]? = new ArrayList[Int]()
        |    b[0] = 5
        |    return 0
        |  }
        |}
        |""".stripMargin
    )
    assert(codes.contains("E0070"), s"expected E0070 in $codes")
    assert(!codes.contains("E0041"), s"must not fall back to the generic E0041 message: $codes")
  }

  it("reports E0070, not the generic E0041 fallback, for compound indexing assignment on a nullable value") {
    val codes = errorCodes(
      """
        |import { java.util.List; java.util.ArrayList; }
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val b: List[Int]? = new ArrayList[Int]()
        |    b[0] += 5
        |    return 0
        |  }
        |}
        |""".stripMargin
    )
    assert(codes.contains("E0070"), s"expected E0070 in $codes")
    assert(!codes.contains("E0041"), s"must not fall back to the generic E0041 message: $codes")
  }

  it("accepts safe indexing read on a nullable value") {
    val result = shell.run(
      """
        |import { java.util.List; java.util.ArrayList; }
        |static def main(args: String[]): Int {
        |  val b: List[Int]? = null
        |  return (b?[0]) ?: 42
        |}
      """.stripMargin,
      "None",
      Array()
    )
    assert(Shell.Success(42) == result)
  }
}
