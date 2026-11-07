package onion.compiler.tools

import onion.tools.Shell

// #2659: named arguments in `extends A(...)` and `: this(...)` were bound by
// position, so `extends A(b = 7, a = 3)` silently set a = 7.
class ConstructorDelegationNamedArgumentSpec extends AbstractShellSpec {
  private val base =
    """class A {
      |  val s: String
      |public:
      |  def this(a: Int = 1, b: Int = 2) { s = "" + a + b }
      |  def this(t: String) : this(b = 8, a = 4) { }
      |  def show(): String = s
      |}
      |""".stripMargin

  private def run(decls: String, expr: String) = shell.run(
    s"""$base
       |$decls
       |class Main {
       |public:
       |  static def main(args: String[]): String = $expr
       |}
       |""".stripMargin,
    "None",
    Array()
  )

  describe("Named arguments in a constructor delegation") {
    it("binds `extends A(b = 7, a = 3)` by name") {
      assert(Shell.Success("37") == run("class C(x: Int) extends A(b = 7, a = 3)", "new C(1).show()"))
    }

    it("binds `: this(b = 8, a = 4)` by name") {
      assert(Shell.Success("48") == run("", "new A(\"s\").show()"))
    }

    it("fills an omitted parameter from its default") {
      assert(Shell.Success("17") == run("class C(x: Int) extends A(b = 7)", "new C(1).show()"))
    }

    it("fills a default for `: this(...)`") {
      val decls =
        """class G {
          |  val s: String
          |public:
          |  def this(a: Int = 1, b: Int = 2) { s = "" + a + b }
          |  def this(t: String) : this(b = 5) { }
          |  def show(): String = s
          |}
          |""".stripMargin
      assert(Shell.Success("15") == run(decls, "new G(\"t\").show()"))
    }

    it("computes a default from the arguments before it") {
      val decls =
        """class H {
          |  val s: String
          |public:
          |  def this(a: Int = 1, b: Int = a * 10) { s = "" + a + "," + b }
          |  def show(): String = s
          |}
          |class K extends H(a = 3)
          |""".stripMargin
      assert(Shell.Success("3,30") == run(decls, "new K().show()"))
    }

    it("mixes positional and named arguments") {
      assert(Shell.Success("95") == run("class C extends A(9, b = 5)", "new C().show()"))
    }

    it("passes a constructor parameter by name") {
      assert(Shell.Success("26") == run("class C(x: Int) extends A(b = x, a = 2)", "new C(6).show()"))
    }

    it("binds by name for a generic parent") {
      val decls =
        """class Box[T] {
          |  val v: T
          |  val n: Int
          |public:
          |  def this(v: T, n: Int = 0) { this.v = v; this.n = n }
          |  def show(): String = "" + v + n
          |}
          |class IntBox extends Box[Int](n = 2, v = 5)
          |""".stripMargin
      assert(Shell.Success("52") == run(decls, "new IntBox().show()"))
    }

    it("rejects an unknown name") {
      assert(Shell.Failure(-1) == run("class C extends A(z = 7)", "new C().show()"))
    }

    it("rejects a name given twice") {
      assert(Shell.Failure(-1) == run("class C extends A(b = 1, b = 2)", "new C().show()"))
    }

    it("rejects a field read in a named delegation argument (E0090)") {
      val decls =
        """class F {
          |  val f: Int = 3
          |public:
          |  def this(a: Int = 1, b: Int = 2) { }
          |  def this(s: String) : this(b = f) { }
          |}
          |""".stripMargin
      assert(Shell.Failure(-1) == run(decls, "\"x\""))
    }
  }

  // Panel block at 4b9d347: a constructor's parameter names were only known once its body
  // was typed, so a named delegation to one declared later failed with E0043.
  describe("A named delegation to a constructor declared later") {
    def runAlone(src: String) = shell.run(src, "None", Array())

    it("binds `: this(z = 1, w = 2)` to the primary constructor") {
      val src =
        """class R(val z: Int, val w: Int) {
          |public:
          |  def this() : this(w = 2, z = 1) { }
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): String { val q = new R(); return "" + q.z + q.w }
          |}
          |""".stripMargin
      assert(Shell.Success("12") == runAlone(src))
    }

    it("fills the primary constructor's default") {
      val src =
        """class R(val z: Int, val w: Int = 9) {
          |public:
          |  def this() : this(z = 1) { }
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): String { val q = new R(); return "" + q.z + q.w }
          |}
          |""".stripMargin
      assert(Shell.Success("19") == runAlone(src))
    }

    it("binds `extends A(b = 7, a = x)` when A comes after the subclass") {
      val src =
        """class C(x: Int) extends B(b = 7, a = x) {
          |}
          |class B {
          |  val s: String
          |public:
          |  def this(a: Int, b: Int) { s = "" + a + b }
          |  def show(): String = s
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): String = new C(3).show()
          |}
          |""".stripMargin
      assert(Shell.Success("37") == runAlone(src))
    }

    it("binds `: this(q = 1L, r = 4)` written above its target") {
      val src =
        """class K {
          |  val s: String
          |public:
          |  def this(t: String) : this(r = 4, q = 1L) { }
          |  def this(q: Long, r: Int) { s = "" + q + r }
          |  def show(): String = s
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): String = new K("t").show()
          |}
          |""".stripMargin
      assert(Shell.Success("14") == runAlone(src))
    }
  }
}
