package onion.compiler.tools

import onion.tools.Shell
import onion.compiler.{CompilationOutcome, CompilerConfig, OnionCompiler, StreamInputSource}
import java.io.StringReader

/**
 * #2916: a private instance method called with named arguments or an untyped
 * lambda argument took a resolution path with no access check, so it compiled
 * and threw IllegalAccessError at run time. `new A().pim(7)` was already E0013.
 * The instance-method twin of #2827 (constructors).
 */
class InstanceMethodAccessSpec extends AbstractShellSpec {
  private def codes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.errorCode.getOrElse("?"))
      case _ => Seq.empty
    }
  }

  private val privateA =
    "class A {\npublic:\n  def this() { }\n" +
    "  def insideNamed(): Int = pim(b = 3, a = 7) + this.pim(b = 1, a = 1)\n" +
    "  def insideLambda(): Int = lam((x) -> x + 1) + this.lam { x -> x }\n" +
    "private:\n  def pim(a: Int, b: Int = 2): Int { return a + b }\n" +
    "  def lam(f: (Int) -> Int): Int { return f(1) }\n}\n"

  private val protectedA =
    "class A {\npublic:\n  def this() { }\n" +
    "protected:\n  def pim(a: Int, b: Int = 2): Int { return a + b }\n" +
    "  def lam(f: (Int) -> Int): Int { return f(1) }\n}\n"

  it("rejects a private method called with named arguments (#2916)") {
    assert(codes(privateA + "println(new A().pim(b = 3, a = 7))\n") == Seq("E0013"))
  }
  it("rejects a private method that takes an untyped lambda (#2916)") {
    assert(codes(privateA + "println(new A().lam((x) -> x + 1))\n") == Seq("E0013"))
  }
  it("rejects a private method that takes a trailing lambda (#2916)") {
    assert(codes(privateA + "println(new A().lam { x -> x + 1 })\n") == Seq("E0013"))
  }
  it("rejects named arguments through a safe call too (#2916)") {
    assert(codes(privateA + "println(new A()?.pim(b = 3, a = 7))\n") == Seq("E0013"))
  }
  it("rejects a protected method reached that way from a class that is not a subclass (#2916)") {
    assert(codes(protectedA + "println(new A().pim(b = 3, a = 7))\n") == Seq("E0013"))
    assert(codes(protectedA + "println(new A().lam((x) -> x + 1))\n") == Seq("E0013"))
  }
  it("rejects an inherited private method called unqualified from a subclass (#2916)") {
    val sub = "class B extends A {\npublic:\n  def this() { }\n"
    assert(codes(privateA + sub + "  def go(): Int = pim(b = 3, a = 7)\n}\n") == Seq("E0013"))
    assert(codes(privateA + sub + "  def go(): Int = lam((x) -> x + 1)\n}\n") == Seq("E0013"))
  }
  it("still allows those private methods from within the class (#2916)") {
    assert(Shell.Success(15) == shell.run(
      privateA + "def main(args: String[]): Int = new A().insideNamed() + new A().insideLambda()\n",
      "None", Array()))
  }
  it("still allows a subclass to reach the protected methods that way (#2916)") {
    assert(Shell.Success(8) == shell.run(
      protectedA +
      "class B extends A {\npublic:\n  def this() { }\n" +
      "  def go(): Int = pim(b = 1, a = 1) + lam((x) -> x) + this.pim(a = 2) + this.lam { x -> x }\n}\n" +
      "def main(args: String[]): Int = new B().go()\n",
      "None", Array()))
  }
}
