package onion.compiler.tools

import onion.tools.Shell

// #2756: with two overloads that share a parameter name, a named argument picked by
// name only, so `f(name = "s")` beside `f(name: Long)` and `f(name: String)` was E0006.
// The argument's type now decides, as it does for the positional call `f("s")`.
class NamedArgumentOverloadSpec extends AbstractShellSpec {
  private val base =
    """class C {
      |public:
      |  def this {}
      |  def f(name: Long): String = "long:" + name
      |  def f(name: String): String = "str:" + name
      |  def g(name: Int): String = "int:" + name
      |  def g(name: Long): String = "long:" + name
      |  def h(name: String, n: Int): String = "two:" + name + n
      |  def h(name: String): String = "one:" + name
      |  def k(a: Int, b: String): String = "ab"
      |  def k(b: String, a: Int): String = "ba"
      |  static def s(v: Long): String = "long"
      |  static def s(v: String): String = "str"
      |  def self1(): String = f(name = "u")
      |}
      |class D extends C {
      |public:
      |  def this {}
      |  def viaSuper(): String = super.f(name = "p")
      |}
      |class G[T] {
      |public:
      |  def this {}
      |  def m(v: T): String = "t"
      |  def m(v: Int): String = "int"
      |}
      |""".stripMargin

  private def run(expr: String) = shell.run(
    s"""$base
       |class Main {
       |public:
       |  static def main(args: String[]): String = $expr
       |}
       |""".stripMargin,
    "None",
    Array()
  )

  describe("A named argument picks among overloads that share its name") {
    it("by the argument's type on an instance call") {
      assert(Shell.Success("str:s") == run("new C().f(name = \"s\")"))
      assert(Shell.Success("long:5") == run("new C().f(name = 5L)"))
    }

    it("the same way as the positional call") {
      assert(Shell.Success("str:s") == run("new C().f(\"s\")"))
    }

    it("on a static, unqualified, safe and super call") {
      assert(Shell.Success("str") == run("C::s(v = \"x\")"))
      assert(Shell.Success("str:u") == run("new C().self1()"))
      assert(Shell.Success("str:q") == run("{ val c: C? = new C(); c?.f(name = \"q\") ?: \"\" }"))
      assert(Shell.Success("str:p") == run("new D().viaSuper()"))
    }

    it("prefers the most specific fit, as a positional call does") {
      assert(run("new C().g(5)") == run("new C().g(name = 5)"))
      assert(Shell.Success("int:5") == run("new C().g(name = 5)"))
    }

    it("drops an overload that would leave a parameter without a value") {
      assert(Shell.Success("one:x") == run("new C().h(name = \"x\")"))
      assert(Shell.Success("two:x1") == run("new C().h(name = \"x\", n = 1)"))
    }

    it("reads a generic receiver's type argument") {
      assert(Shell.Success("t") == run("new G[String]().m(v = \"x\")"))
      assert(Shell.Success("int") == run("new G[String]().m(v = 1)"))
    }

    it("stays E0006 when both overloads fit equally") {
      assert(Shell.Failure(-1) == run("new C().k(a = 1, b = \"x\")"))
    }
  }
}
