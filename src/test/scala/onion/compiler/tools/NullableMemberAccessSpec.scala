package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * Dereferencing a nullable value's field directly (`x.length` where `x: String?`)
 * reports a clean null-safety error (E0070) that points at `?.`/`?:`/`!!`/a null
 * check — matching the method-call path — instead of the misleading
 * "Object expected" (E0000) the generic fallback used to produce.
 */
class NullableMemberAccessSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  it("rejects direct field access on a nullable value") {
    val result = shell.run(
      """
        | static def main(args: String[]): Int {
        |   val x: String? = "abc"
        |   return x.length
        | }
      """.stripMargin,
      "None",
      Array()
    )
    assert(Shell.Failure(-1) == result)
  }

  it("reports E0070, not the generic E0000 fallback, for direct field access on a nullable value") {
    val codes = errorCodes(
      """
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val x: String? = "abc"
        |    return x.length
        |  }
        |}
        |""".stripMargin
    )
    assert(codes.contains("E0070"), s"expected E0070 in $codes")
    assert(!codes.contains("E0000"), s"must not fall back to the generic E0000 message: $codes")
  }

  it("accepts safe field access on a nullable value") {
    val result = shell.run(
      """
        | static def main(args: String[]): Int {
        |   val x: String? = "abc"
        |   return x?.length ?: 0
        | }
      """.stripMargin,
      "None",
      Array()
    )
    assert(Shell.Success(3) == result)
  }

  it("rejects direct method call on a nullable class-typed value") {
    val result = shell.run(
      """
        |class Box {
        |  val value: Int
        |public:
        |  def this(v: Int) { value = v }
        |  def get: Int = value
        |}
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val b: Box? = new Box(1)
        |    return b.get()
        |  }
        |}
      """.stripMargin,
      "None",
      Array()
    )
    assert(Shell.Failure(-1) == result)
  }

  it("reports E0070, not the generic E0041 fallback, for a direct method call on a nullable value") {
    val codes = errorCodes(
      """
        |class Box {
        |  val value: Int
        |public:
        |  def this(v: Int) { value = v }
        |  def get: Int = value
        |}
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val b: Box? = new Box(1)
        |    return b.get()
        |  }
        |}
        |""".stripMargin
    )
    assert(codes.contains("E0070"), s"expected E0070 in $codes")
    assert(!codes.contains("E0041"), s"must not fall back to the generic E0041 message: $codes")
  }

  it("accepts safe method call on a nullable value") {
    val result = shell.run(
      """
        |class Box {
        |  val value: Int
        |public:
        |  def this(v: Int) { value = v }
        |  def get: Int = value
        |}
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val b: Box? = new Box(1)
        |    return b?.get() ?: 0
        |  }
        |}
      """.stripMargin,
      "None",
      Array()
    )
    assert(Shell.Success(1) == result)
  }
}
