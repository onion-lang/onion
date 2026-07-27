package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * `s::m()` where `s` is a local variable is the Java/Kotlin habit of using the
 * static-member operator for an instance call. Instead of the generic
 * "type s not found" (E0003), the compiler points at `.` (E0071). A genuine
 * static call on a type name is unaffected.
 */
class StaticCallOnInstanceSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  it("reports E0071 for :: on a local variable") {
    val result = shell.run(
      """
        | static def main(args: String[]): void {
        |   val s: String = "hi"
        |   IO::println(s::length())
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Failure(-1) == result)
  }

  it("reports the E0071 code specifically (not just a generic failure)") {
    assert(errorCodes(
      """
        |class Test {
        |public:
        |  static def main(args: String[]): void {
        |    val s: String = "hi"
        |    IO::println(s::length())
        |  }
        |}
        |""".stripMargin
    ).contains("E0071"))
  }

  it("still allows a genuine static call on a type") {
    val result = shell.run(
      """
        | static def main(args: String[]): String {
        |   return Long::toString(42L)
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success("42") == result)
  }

  it("a real type wins over a like-named local (no E0071 misfire)") {
    val result = shell.run(
      """
        | class Helper { public: static def greet(): String = "hi" }
        | class Main { public: static def main(args: String[]): String {
        |   val Helper = "shadow"
        |   return Helper::greet()
        | }}
      """.stripMargin, "None", Array())
    assert(Shell.Success("hi") == result)
  }
}
