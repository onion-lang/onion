package onion.compiler.tools

import onion.tools.Shell
import onion.compiler.{CompilationOutcome, CompilerConfig, OnionCompiler, StreamInputSource}
import java.io.StringReader

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

  // #2827: a private constructor reached through a default, a named argument
  // or an untyped lambda argument took a resolution path with no access
  // check, so it compiled and threw IllegalAccessError at run time.
  private def codes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.errorCode.getOrElse("?"))
      case _ => Seq.empty
    }
  }
  private val privateDefaulted =
    "class A {\nprivate:\n  def this(a: Int, b: Int = 2) { }\npublic:\n  def this() { }\n" +
    "  static def inside(): A = new A(7)\n  static def insideNamed(): A = new A(b = 3, a = 7)\n}\n" +
    "class L {\nprivate:\n  def this(f: (Int) -> Int) { }\npublic:\n  def this() { }\n" +
    "  static def inside(): L = new L((x) -> x + 1)\n}\n"

  it("rejects a private constructor whose default fills an omitted argument (#2827)") {
    assert(codes(privateDefaulted + "new A(7)\n") == Seq("E0013"))
  }
  it("rejects a private constructor called with named arguments (#2827)") {
    assert(codes(privateDefaulted + "new A(b = 3, a = 7)\n") == Seq("E0013"))
  }
  it("rejects a private constructor that takes an untyped lambda (#2827)") {
    assert(codes(privateDefaulted + "new L((x) -> x + 1)\n") == Seq("E0013"))
  }
  it("still allows those private constructors from within the class (#2827)") {
    assert(Shell.Success(3) == shell.run(
      privateDefaulted + "def main(args: String[]): Int {\n  A::inside(); A::insideNamed(); L::inside()\n  return 3\n}\n",
      "None", Array()))
  }
}
