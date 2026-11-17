package onion.compiler.tools

import onion.tools.Shell

/**
 * A lambda passed as a named argument (`f = (x) -> ...`) writes through to the `var`s it
 * captures, the same as a positional lambda. Before #3021 the capture scan never looked
 * inside a named argument, so the variable was copied instead of shared and every write
 * was lost.
 */
class NamedLambdaCaptureSpec extends AbstractShellSpec {
  describe("a lambda passed as a named argument") {
    it("writes to a captured var through an unqualified call") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def ap(f: (Int) -> Int): Int = f(10)
          |  static def main(args: String[]): String {
          |    var a = 0
          |    ap((x: Int) -> { a += x; return a })
          |    var b = 0
          |    ap(f = (x: Int) -> { b += x; return b })
          |    return "positional=" + a + " named=" + b
          |  }
          |}
          |""".stripMargin, "NamedLambdaCaptureUnqualified.on", Array())
      assert(result == Shell.Success("positional=10 named=10"))
    }

    it("writes to a captured var with an untyped parameter") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def ap(f: (Int) -> Int): Int = f(10)
          |  static def main(args: String[]): String {
          |    var b = 0
          |    ap(f = (x) -> { b += x; return b })
          |    return "named=" + b
          |  }
          |}
          |""".stripMargin, "NamedLambdaCaptureUntyped.on", Array())
      assert(result == Shell.Success("named=10"))
    }

    it("writes to a captured var through instance, static and constructor calls") {
      val result = shell.run(
        """
          |class C {
          |public:
          |  def this { }
          |  def this(n: Int, f: (Int) -> Int) { f(n) }
          |  def m(n: Int, f: (Int) -> Int): Int = f(n)
          |  static def s(n: Int, f: (Int) -> Int): Int = f(n)
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    var a = 0
          |    new C().m(1, f = (x: Int) -> { a += x; return a })
          |    var b = 0
          |    C::s(2, f = (x: Int) -> { b += x; return b })
          |    var c = 0
          |    new C(3, f = (x: Int) -> { c += x; return c })
          |    return "" + a + " " + b + " " + c
          |  }
          |}
          |""".stripMargin, "NamedLambdaCaptureMembers.on", Array())
      assert(result == Shell.Success("1 2 3"))
    }

    it("shares the var with a nested lambda inside the named one") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def ap(f: (Int) -> Int): Int = f(10)
          |  static def twice(g: () -> void): void { g(); g() }
          |  static def main(args: String[]): String {
          |    var b = 0
          |    ap(f = (x: Int) -> { twice(g = () -> { b += x }); return b })
          |    return "nested=" + b
          |  }
          |}
          |""".stripMargin, "NamedLambdaCaptureNested.on", Array())
      assert(result == Shell.Success("nested=20"))
    }
  }
}
