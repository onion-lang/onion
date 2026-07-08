package onion.compiler.tools

import onion.tools.Shell

/**
 * Dereferencing a nullable value's field directly (`x.length` where `x: String?`)
 * reports a clean null-safety error (E0070) that points at `?.`/`?:`/`!!`/a null
 * check — matching the method-call path — instead of the misleading
 * "Object expected" (E0000) the generic fallback used to produce.
 */
class NullableMemberAccessSpec extends AbstractShellSpec {
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
}
