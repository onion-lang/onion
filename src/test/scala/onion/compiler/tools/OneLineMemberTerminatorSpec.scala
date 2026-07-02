package onion.compiler.tools

import onion.tools.Shell

/**
 * A field or `forward` member that is the last member on the same line as the
 * class's closing brace terminates at the `}` (it used to require a newline or a
 * semicolon; `class C { public: var x: Int = 0 }` was a syntax error).
 */
class OneLineMemberTerminatorSpec extends AbstractShellSpec {
  it("allows a field as the last member on the closing-brace line") {
    assert(Shell.Success(5) == shell.run(
      "class C { public: var x: Int = 5 }\ndef main(args: String[]): Int { val c = new C()\n return c.x }", "None", Array()))
  }
  it("allows a forward field on the closing-brace line") {
    assert(Shell.Success("hi") == shell.run(
      "interface Greet { def hi(): String }\nclass Impl <: Greet { public: def this{}\n def hi(): String = \"hi\" }\nclass C <: Greet { public: def this{}\n forward val g: Greet = new Impl() }\ndef main(args: String[]): String { return new C().hi() }", "None", Array()))
  }
  it("still parses multi-line fields") {
    assert(Shell.Success(3) == shell.run(
      "class C {\npublic:\n val x: Int = 1\n val y: Int = 2\n def sum(): Int = x + y\n}\ndef main(args: String[]): Int { return new C().sum() }", "None", Array()))
  }
}
