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
      |}
      |example l1 {
      |  val s = new FW()
      |  val v = new P("KOTA", 42)
      |  s.parse(s.print(v)).get() == v
      |}
      |""".stripMargin

  describe("a user-written fixed-width shape") {
    it("parses, prints, and satisfies its machine-checked L1 example") {
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

  describe("the law gate (E0080)") {
    it("rejects a Shape instance whose file asserts no law") {
      val noLaw = fixedWidth.replace(
        """example l1 {
          |  val s = new FW()
          |  val v = new P("KOTA", 42)
          |  s.parse(s.print(v)).get() == v
          |}
          |""".stripMargin, "")
      val r = shell.run(noLaw +
        """class Test {
          |public:
          |  static def main(args: String[]): Int { return 0 }
          |}
          |""".stripMargin, "None", Array())
      assert(r.isInstanceOf[Shell.Failure], s"expected E0080 failure, got $r")
    }

    it("a record `law` clause in the same file satisfies the gate too") {
      val viaRecordLaw = fixedWidth.replace(
        """example l1 {
          |  val s = new FW()
          |  val v = new P("KOTA", 42)
          |  s.parse(s.print(v)).get() == v
          |}
          |""".stripMargin, "")
        .replace("record P(name: String, amount: Int)",
          """record P(name: String, amount: Int)
            |  example nonEmpty { new FW().print(new P("A", 1)).length() == 16 }""".stripMargin)
      val r = shell.run(viaRecordLaw +
        """class Test {
          |public:
          |  static def main(args: String[]): Int { return 0 }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(0) == r, r.toString)
    }

    it("does not fire on interfaces or on classes unrelated to Shape") {
      val r = shell.run(
        """interface Marker { def m(): Int }
          |class Plain conforms Marker {
          |public:
          |  def this {}
          |  def m(): Int = 1
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int { return new Plain().m() }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(1) == r, r.toString)
    }
  }

  describe("top-level examples (the vehicle for user-shape laws)") {
    it("a failing top-level example stops the build with E0065") {
      val r = shell.run(
        """example wrong { 2 + 2 == 5 }
          |class Test {
          |public:
          |  static def main(args: String[]): Int { return 0 }
          |}
          |""".stripMargin, "None", Array())
      assert(r.isInstanceOf[Shell.Failure], s"expected E0065 failure, got $r")
    }

    it("`example` stays an ordinary identifier") {
      val r = shell.run(
        """class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val example = 21
          |    return example * 2
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(42) == r, r.toString)
    }
  }
}
