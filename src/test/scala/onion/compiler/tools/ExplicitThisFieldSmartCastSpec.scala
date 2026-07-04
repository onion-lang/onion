package onion.compiler.tools

import onion.tools.Shell

/**
 * Issue #277: an explicit `this.field` / `self.field` read of a `val` nullable
 * field is smart-cast after a null check, just like the bare field name.
 * A `var` field is never narrowed (it could change between check and use).
 */
class ExplicitThisFieldSmartCastSpec extends AbstractShellSpec {
  describe("explicit this.field smart cast") {
    it("narrows this.field in the then-branch of a null check") {
      val r = shell.run(
        """
          |class C {
          |  val name: String?
          |public:
          |  def this(n: String?) { this.name = n }
          |  def show(): String {
          |    if this.name != null { return this.name.toUpperCase() } else { return "none" }
          |  }
          |}
          |def main(args: String[]): String { return new C("hi").show() + "/" + new C(null).show() }
          |""".stripMargin, "None", Array())
      assert(Shell.Success("HI/none") == r)
    }

    it("narrows self.field the same way") {
      val r = shell.run(
        """
          |class C {
          |  val name: String?
          |public:
          |  def this(n: String?) { self.name = n }
          |  def show(): String {
          |    if self.name != null { return self.name.toUpperCase() } else { return "none" }
          |  }
          |}
          |def main(args: String[]): String { return new C("yo").show() }
          |""".stripMargin, "None", Array())
      assert(Shell.Success("YO") == r)
    }

    it("narrows this.field in the else-branch of an == null check") {
      val r = shell.run(
        """
          |class C {
          |  val name: String?
          |public:
          |  def this(n: String?) { this.name = n }
          |  def show(): String {
          |    if this.name == null { return "none" } else { return this.name.toUpperCase() }
          |  }
          |}
          |def main(args: String[]): String { return new C("eq").show() + "/" + new C(null).show() }
          |""".stripMargin, "None", Array())
      assert(Shell.Success("EQ/none") == r)
    }

    it("narrows two this.fields joined by &&") {
      val r = shell.run(
        """
          |class C {
          |  val a: String?
          |  val b: String?
          |public:
          |  def this(x: String?, y: String?) { this.a = x; this.b = y }
          |  def show(): String {
          |    if this.a != null && this.b != null { return this.a.toUpperCase() + this.b.toUpperCase() }
          |    else { return "none" }
          |  }
          |}
          |def main(args: String[]): String { return new C("x","y").show() + "/" + new C("x",null).show() }
          |""".stripMargin, "None", Array())
      assert(Shell.Success("XY/none") == r)
    }

    it("does NOT narrow an explicit this.var field") {
      val r = shell.run(
        """
          |class C {
          |  var name: String?
          |public:
          |  def this(n: String?) { this.name = n }
          |  def show(): String {
          |    if this.name != null { return this.name.toUpperCase() } else { return "none" }
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Failure(-1) == r)
    }
  }
}
