package onion.compiler.tools

import onion.tools.Shell

/**
 * A no-body (abstract/interface) method declaration immediately followed by `}`
 * on the same line must parse; previously it terminated with eos() (which does
 * not accept `}`), so `interface A { def f(): Int }` was a syntax error.
 */
class OneLineMethodDeclSpec extends AbstractShellSpec {
  private def ok(program: String, expected: Int): Unit =
    assert(Shell.Success(expected) == shell.run(program, "None", Array()))

  describe("one-line method declarations") {
    it("one-line interface with an abstract method") {
      ok("interface A { def f(): Int }\nclass C <: A { public: def this {} def f(): Int { return 5 } }\ndef main(args: String[]): Int { return new C().f() }", 5)
    }
    it("one-line interface with a = expr default method") {
      ok("interface A { def f(): Int = 6 }\nclass C <: A { public: def this {} }\ndef main(args: String[]): Int { return new C().f() }", 6)
    }
    it("still parses the = expr method form (regression)") {
      ok("class C { public: def this {} def f(): Int = 42 }\ndef main(args: String[]): Int { return new C().f() }", 42)
    }
    it("still parses interface default methods with block bodies (regression)") {
      ok("interface A {\n  def f(): Int { return 9 }\n}\nclass C <: A { public: def this {} }\ndef main(args: String[]): Int { return new C().f() }", 9)
    }
  }
}
