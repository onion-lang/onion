package onion.compiler.tools

import onion.tools.Shell

/**
 * A scheme-prefixed raw literal `prefix"..."` desugars to `prefix("...")` for ANY
 * identifier prefix, not just the built-in re/file/http. So a user can define
 * their own prefix simply by defining a function of that name — no new machinery.
 */
class UserSchemeLiteralSpec extends AbstractShellSpec {
  it("a user-defined prefix resolves to the user's function") {
    val result = shell.run(
      """
        | def sql(query: String): String = "[SQL] " + query.trim()
        | static def main(args: String[]): String {
        |   return sql"SELECT * FROM t"
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success("[SQL] SELECT * FROM t") == result)
  }

  it("passes the body verbatim (raw, no escape processing)") {
    val result = shell.run(
      """
        | def raw(s: String): Int = s.length()
        | static def main(args: String[]): Int {
        |   return raw"a\d+b"
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success(5) == result) // a \ d + b = 5 chars, backslash verbatim
  }

  it("an undefined prefix is a clean method-not-found, not a lex error") {
    val result = shell.run(
      """
        | static def main(args: String[]): void {
        |   IO::println(nope"x")
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Failure(-1) == result)
  }
}
