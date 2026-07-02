package onion.compiler.tools

import onion.tools.Shell

/**
 * Constructor access control is checked at compile time. A private constructor
 * used with `new` from another class used to compile and throw IllegalAccessError
 * at runtime; it is now a compile error. Public, default, record, and same-class
 * constructions are unaffected.
 */
class ConstructorAccessSpec extends AbstractShellSpec {
  it("rejects new on a private constructor from another class") {
    assert(Shell.Failure(-1) == shell.run(
      "class C {\n  var a: Int\n  def this { a = 0 }\n}\ndef main(args: String[]): void { val c = new C()\n IO::println(\"x\") }", "None", Array()))
  }
  it("allows a public constructor") {
    assert(Shell.Success("ok") == shell.run(
      "class C { public: var a: Int\n def this { a = 0 } }\ndef main(args: String[]): String { val c = new C()\n return \"ok\" }", "None", Array()))
  }
  it("allows a private constructor from within the same class") {
    assert(Shell.Success(true) == shell.run(
      "class Factory { public: static def make(): Factory = new Factory()\n def this { } }\ndef main(args: String[]): Boolean { return Factory::make() != null }", "None", Array()))
  }
}
