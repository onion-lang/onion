package onion.compiler.tools

import onion.tools.Shell

/**
 * A type parameter whose bound refers to the class being declared, with itself as a
 * type argument (`class C[T extends C[T]]`, the CRTP / recursive self-bound pattern),
 * is accepted. It used to fail with E0030 because the class's arity was not yet
 * registered while its own bound was resolved. The bound is still enforced.
 */
class SelfReferentialBoundSpec extends AbstractShellSpec {
  it("declares a class with a self-referential F-bound") {
    assert(Shell.Success("ok") == shell.run(
      "class C[T extends C[T]] { public: def this{} }\ndef main(args: String[]): String { return \"ok\" }", "None", Array()))
  }
  it("declares a record with a self-referential F-bound") {
    assert(Shell.Success("ok") == shell.run(
      "record Box[T extends Box[T]](v: Int)\ndef main(args: String[]): String { return \"ok\" }", "None", Array()))
  }
  it("still enforces the self-referential bound at instantiation") {
    assert(Shell.Failure(-1) == shell.run(
      "class C[T extends C[T]] { public: def this{} }\ndef main(args: String[]): void { val c = new C[String]() }", "None", Array()))
  }
  it("still accepts an external F-bound") {
    assert(Shell.Success("ok") == shell.run(
      "class Box[T extends Comparable[T]] { public: def this{} }\ndef main(args: String[]): String { return \"ok\" }", "None", Array()))
  }
}
