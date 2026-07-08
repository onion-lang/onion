package onion.compiler.tools

import onion.tools.Shell

/**
 * `s::m()` where `s` is a local variable is the Java/Kotlin habit of using the
 * static-member operator for an instance call. Instead of the generic
 * "type s not found" (E0003), the compiler points at `.` (E0071). A genuine
 * static call on a type name is unaffected.
 */
class StaticCallOnInstanceSpec extends AbstractShellSpec {
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

  it("still allows a genuine static call on a type") {
    val result = shell.run(
      """
        | static def main(args: String[]): String {
        |   return Long::toString(42L)
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success("42") == result)
  }
}
