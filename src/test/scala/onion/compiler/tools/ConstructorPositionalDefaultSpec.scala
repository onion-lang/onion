package onion.compiler.tools

import onion.tools.Shell

// #2660: an omitted trailing argument was not filled from its default in
// `extends A(7)` / `: this(4)` (E0021), and `new A(7)` was ambiguous (E0022)
// next to a constructor of the same arity whose parameter type cannot take it.
class ConstructorPositionalDefaultSpec extends AbstractShellSpec {
  private val base =
    """class A {
      |  val s: String
      |public:
      |  def this(a: Int = 1, b: Int = 2) { s = "" + a + b }
      |  def this(t: String) : this(4) { }
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

  describe("A positional constructor call with an omitted default") {
    it("fills the default in `extends A(7)`") {
      assert(Shell.Success("72") == run("class D extends A(7) { }", "new D().show()"))
    }

    it("fills the default in `: this(4)`") {
      assert(Shell.Success("42") == run("", "new A(\"s\").show()"))
    }

    it("picks A(Int, Int) for `new A(7)` next to A(String)") {
      assert(Shell.Success("72") == run("", "new A(7).show()"))
    }

    it("still picks A(String) for `new A(\"s\")`") {
      assert(Shell.Success("42") == run("", "new A(\"s\").show()"))
    }

    it("picks by type when two constructors each have a default") {
      val decls =
        """class C {
          |public:
          |  val v: Int
          |  def this(s: String, b: Int = 5) { this.v = s.length() + b }
          |  def this(n: Int, d: Int = 2) { this.v = n + d }
          |}
          |""".stripMargin
      assert(Shell.Success("7") == run(decls, "\"\" + new C(\"ab\").v"))
      assert(Shell.Success("102") == run(decls, "\"\" + new C(100).v"))
    }

    it("picks by type in `extends` when two parent constructors each have a default") {
      val decls =
        """class P {
          |public:
          |  val v: Int
          |  def this(s: String, b: Int = 5) { this.v = s.length() + b }
          |  def this(n: Int, d: Int = 2) { this.v = n + d }
          |}
          |class Q extends P("abc") { }
          |class R extends P(10) { }
          |""".stripMargin
      assert(Shell.Success("8 12") == run(decls, "new Q().v + \" \" + new R().v"))
    }

    it("still reports E0022 when both constructors accept the argument") {
      val decls =
        """class E {
          |public:
          |  def this(o: Object, b: Int = 5) { }
          |  def this(o: Object, s: String = "x") { }
          |}
          |""".stripMargin
      assert(Shell.Failure(-1) == run(decls, "\"\" + new E(1)"))
    }

    it("reports E0021, not E0022, for an argument no constructor takes") {
      val out = new java.io.ByteArrayOutputStream()
      val ps = new java.io.PrintStream(out, true, "UTF-8")
      val savedErr = System.err
      val result =
        try {
          System.setErr(ps)
          Console.withErr(ps) { run("class F extends A(true) { }\nclass G extends A(false, 1) { }", "\"\" + new A(true)") }
        } finally System.setErr(savedErr)
      assert(Shell.Failure(-1) == result)
      val err = out.toString
      assert(err.contains("E0021"), err)
      assert(!err.contains("E0022"), err)
    }
  }
}
