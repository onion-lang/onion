package onion.compiler.tools

import onion.tools.Shell

/**
 * The eight scalar component types a boundary derivation can convert are enumerated in
 * five separate places — `cliKindOf`, `convertCliValue`, `convertCapturedValue`,
 * `jsonGetterOf` and `Cli`'s runtime helpers — plus a sixth, type-level copy in
 * `TypingOutlinePass`. Adding one type means six edits in two languages.
 *
 * This spec pins the invariant those copies are supposed to maintain: every supported
 * type behaves the same way through every derivation. It is what makes collapsing them
 * onto one table a refactor rather than a rewrite.
 */
class ScalarConversionSpec extends AbstractShellSpec {

  describe("every supported scalar round-trips through `from re\"...\"`") {
    it("parses and formats all eight component types") {
      val r = shell.run(
        """
          |record All(s: String, i: Int, l: Long, d: Double, f: Float, b: Boolean, sh: Short, by: Byte)
          |  from re"(\w+) (-?\d+) (-?\d+) (-?[\d.]+) (-?[\d.]+) (\w+) (-?\d+) (-?\d+)"
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val v = All::parse("hi -1 -2 1.5 2.5 true -3 -4")
          |    if v == null { return "parse failed" }
          |    return v.s() + "|" + v.i() + "|" + v.l() + "|" + v.d() + "|" +
          |           v.f() + "|" + v.b() + "|" + v.sh() + "|" + v.by()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("hi|-1|-2|1.5|2.5|true|-3|-4") == r)
    }
  }

  describe("every supported scalar round-trips through derive!(Json)") {
    it("serializes and reads back all eight component types") {
      val r = shell.run(
        """
          |record All(s: String, i: Int, l: Long, d: Double, f: Float, b: Boolean, sh: Short, by: Byte)
          |  derive!(Json)
          |class Test {
          |public:
          |  static def main(args: String[]): Boolean {
          |    val v = new All("hi", -1, -2L, 1.5, 2.5f, true, (-3 as Short), (-4 as Byte))
          |    return All::fromJson(All::toJson(v)) == v
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(true) == r)
    }
  }

  describe("a Boolean capture is validated, not silently coerced") {
    // java.lang.Boolean.parseBoolean never throws: it maps anything that is not
    // "true" to false. So `from re"..."` quietly turned a malformed field into a
    // valid-looking `false`, which is the one failure mode a parser must never have.
    it("rejects a Boolean field that is neither true nor false") {
      val r = shell.run(
        """
          |record Flag(name: String, on: Boolean) from re"(\w+)=(\w+)"
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val v = Flag::parse("debug=maybe")
          |    return if v == null { "rejected" } else { "silently " + v.on() }
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("rejected") == r)
    }

    it("still accepts true and false, in either case") {
      val r = shell.run(
        """
          |record Flag(name: String, on: Boolean) from re"(\w+)=(\w+)"
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a = Flag::parse("debug=true")
          |    val b = Flag::parse("debug=FALSE")
          |    if a == null || b == null { return "unexpectedly rejected" }
          |    return "" + a.on() + "/" + b.on()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("true/false") == r)
    }

    it("leaves a numeric field's rejection behaving as before") {
      val r = shell.run(
        """
          |record N(v: Int) from re"(\w+)"
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return if N::parse("abc") == null { "rejected" } else { "accepted" }
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("rejected") == r)
    }
  }
}
