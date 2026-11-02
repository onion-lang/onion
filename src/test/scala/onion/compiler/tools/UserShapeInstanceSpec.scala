package onion.compiler.tools

import onion.tools.Shell

/**
 * Shape opened to user-written instances (issue #364): a class conforming
 * `onion.Shape[T]` supplies parse/print for a format the compiler has never heard of,
 * inherits the whole combinator vocabulary, and must assert its laws — the file needs
 * a machine-checked `law` or `example`, or the class does not compile (E0080).
 */
class UserShapeInstanceSpec extends AbstractShellSpec {

  private val fixedWidth =
    """record P(name: String, amount: Int)
      |class FW conforms Shape[P] {
      |public:
      |  def this {}
      |  def parse(text: String, origin: Origin): Outcome[P] {
      |    if text == null || text.length() != 16 {
      |      return Outcome::bad(Defect::at(origin, "", "a 16-column record", "" + text))
      |    }
      |    try {
      |      return Outcome::ok(new P(text.substring(0, 10).trim(), Integer::parseInt(text.substring(10, 16))))
      |    } catch e: NumberFormatException {
      |      return Outcome::bad(Defect::at(origin, "amount", "Int", text.substring(10, 16)))
      |    }
      |  }
      |  def canPrint(): Boolean = true
      |  def print(v: P): String {
      |    val name = new StringBuilder(v.name())
      |    while name.length() < 10 { name.append(" ") }
      |    var amt = "" + v.amount()
      |    while amt.length() < 6 { amt = "0" + amt }
      |    return name.toString() + amt
      |  }
      |  def describe(): String = "fixed-width P"
      |  def printable(v: P): Boolean =
      |    v.name().length() <= 10 && v.name() == v.name().trim() && v.amount() >= 0 && v.amount() <= 999999
      |}
      |def l1(): Boolean
      |  ensures result
      |{
      |  val s = new FW()
      |  val v = new P("KOTA", 42)
      |  return s.parse(s.print(v)).get() == v
      |}
      |""".stripMargin

  describe("a user-written fixed-width shape") {
    it("parses, prints, and satisfies its machine-checked L1 predicate") {
      val r = shell.run(fixedWidth +
        """class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val s = new FW()
          |    return s.print(new P("AB", 7)) + "|" + s.parse("AB        000007").get().name()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("AB        000007|AB") == r, r.toString)
    }

    it("inherits the combinators: eachLine keeps rows and defects apart") {
      val r = shell.run(fixedWidth +
        """class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val each = new FW().eachLine("ALICE     000100\nbroken\nBOB       000205")
          |    val bad = Outcome::defects(each)
          |    return Outcome::values(each).size + "/" + bad.size + "/" +
          |           (bad[0] as Defect).origin().line()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("2/1/2") == r, r.toString)
    }

    it("reports failures as positioned defects with the declared vocabulary") {
      val r = shell.run(fixedWidth +
        """class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val bad = new FW().parse("ALICE     00zz07")
          |    return (bad.defects()[0] as Defect).path() + "/" + (bad.defects()[0] as Defect).expected()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("amount/Int") == r, r.toString)
    }
  }
}
