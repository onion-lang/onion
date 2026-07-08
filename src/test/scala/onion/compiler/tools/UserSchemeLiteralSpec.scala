package onion.compiler.tools

import onion.tools.Shell

/**
 * User-definable scheme-prefixed raw literals: for ANY identifier prefix,
 * {@code prefix"..."} desugars to the unqualified call {@code prefix("...")}
 * with the body kept RAW (backslashes verbatim, no escape processing) — the
 * same behavior the built-in re/file/http prefixes have. A user defines a
 * custom prefix simply by defining a function of that name.
 */
class UserSchemeLiteralSpec extends AbstractShellSpec {
  describe("user-defined scheme-prefixed literals") {
    it("a user prefix desugars to prefix(\"raw\")") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def sql(q: String): String = "[SQL] " + q
          |  static def main(args: String[]): String {
          |    return sql"SELECT * FROM t"
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("[SQL] SELECT * FROM t") == result)
    }

    it("multiple user prefixes coexist") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def money(m: String): String = "money=" + m
          |  static def slug(s: String): String = s.replace(" ", "-")
          |  static def main(args: String[]): String {
          |    return money"$19.99" + "|" + slug"hello world"
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("money=$19.99|hello-world") == result)
    }

    it("the body is RAW — backslashes and escaped quotes pass through verbatim") {
      // Onion source raw"a\d\"b": the raw body is the 6 chars a \ d \ " b.
      // (In this Scala string, \\ is one backslash and \" is one double-quote.)
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def raw(s: String): Int = s.length()
          |  static def main(args: String[]): Int {
          |    return raw"a\d\"b"
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(6) == result)
    }

    it("an undefined prefix is a clean method-not-found, not a crash") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return nope"hi"
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }
  }
}
