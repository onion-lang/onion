package onion.compiler.tools

import onion.tools.Shell

/**
 * Static method access control is checked at compile time. A private static method
 * used via `C::m()` from another class used to compile and throw IllegalAccessError
 * at runtime; it is now a compile error, mirroring instance-method and constructor
 * access (E0013).
 */
class StaticMethodAccessSpec extends AbstractShellSpec {
  it("rejects a private static method called from another class") {
    assert(Shell.Failure(-1) == shell.run(
      "class C { private: static def s(): Int = 1 }\ndef main(args: String[]): void { IO::println(C::s()) }", "None", Array()))
  }
  it("allows a public static method") {
    assert(Shell.Success(1) == shell.run(
      "class C { public: static def pub(): Int = 1 }\ndef main(args: String[]): Int { return C::pub() }", "None", Array()))
  }
  it("allows a private static method from within the same class") {
    assert(Shell.Success(42) == shell.run(
      "class C { public: static def pub(): Int = priv()\n private: static def priv(): Int = 42 }\ndef main(args: String[]): Int { return C::pub() }", "None", Array()))
  }
}
