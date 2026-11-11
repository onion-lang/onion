package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import onion.tools.Shell

import java.io.StringReader

/**
 * `super.m()` reaching an interface default method.
 *
 *  - #2675: a class that conforms to a trait or interface directly could not reach its
 *    default at all (E0005 that listed the method as available). Java writes this
 *    `I.super.m()`; Onion's `super.m()` now falls back to the class's own interfaces when
 *    the superclass chain has no such method.
 *  - #2702: a default reached through an abstract superclass compiled, then threw
 *    IncompatibleClassChangeError, because the call named the interface as a class method.
 */
class SuperInterfaceDefaultSpec extends AbstractShellSpec {

  private def run(code: String): Shell.Result = shell.run(code, "None", Array())

  private def errorCodes(code: String): Seq[String] = {
    val result = new OnionCompiler(CompilerConfig(Seq("."), null, "UTF-8", "", 10)).compileDetailed(
      Seq(new StreamInputSource(() => new StringReader(code), "Test.on"))
    )
    assert(result.hasErrors, "expected a compile error")
    result.allErrors.flatMap(_.errorCode)
  }

  describe("super.m() on a class that conforms to the interface directly (#2675)") {
    it("reaches a trait's default method") {
      assert(Shell.Success("HELLO") == run(
        "trait Greeter {\n  def greet(): String = \"hello\"\n}\n" +
        "class Loud conforms Greeter {\npublic:\n  def this {}\n" +
        "  override def greet(): String = super.greet().toUpperCase()\n}\n" +
        "class Main { public: static def main(args: String[]): String { return new Loud().greet() } }"))
    }

    it("reaches an interface's default method, with an argument") {
      assert(Shell.Success(41) == run(
        "interface A { def g(x: Int): Int = x * 10 }\n" +
        "class C conforms A {\npublic:\n  def this {}\n" +
        "  override def g(x: Int): Int = super.g(x) + 1\n}\n" +
        "class Main { public: static def main(args: String[]): Int { return new C().g(4) } }"))
    }

    it("reaches a default inherited by a direct superinterface") {
      assert(Shell.Success(41) == run(
        "interface A { def g(x: Int): Int = x * 10 }\n" +
        "interface B conforms A { }\n" +
        "class C conforms B {\npublic:\n  def this {}\n" +
        "  override def g(x: Int): Int = super.g(x) + 1\n}\n" +
        "class Main { public: static def main(args: String[]): Int { return new C().g(4) } }"))
    }

    it("substitutes a generic interface's type argument") {
      assert(Shell.Success("[HI]") == run(
        "interface Box[T] { def wrap(x: T): String = \"[\" + x + \"]\" }\n" +
        "class SB conforms Box[String] {\npublic:\n  def this {}\n" +
        "  override def wrap(x: String): String = super.wrap(x.toUpperCase())\n}\n" +
        "class Main { public: static def main(args: String[]): String { return new SB().wrap(\"hi\") } }"))
    }

    it("still prefers the superclass's method, as Java does") {
      assert(Shell.Success("BASE") == run(
        "trait Greeter {\n  def greet(): String = \"hello\"\n}\n" +
        "class Base { public: def this {} def greet(): String = \"base\" }\n" +
        "class Loud extends Base conforms Greeter {\npublic:\n  def this {}\n" +
        "  override def greet(): String = super.greet().toUpperCase()\n}\n" +
        "class Main { public: static def main(args: String[]): String { return new Loud().greet() } }"))
    }

    it("reports two different defaults as ambiguous, once (E0006)") {
      val codes = errorCodes(
        "interface A { def g(): Int = 1 }\n" +
        "interface B { def g(): Int = 2 }\n" +
        "class C conforms A, B {\npublic:\n  def this {}\n" +
        "  override def g(): Int = super.g()\n}\n")
      assert(codes == Seq("E0006"), s"expected exactly one E0006, got: $codes")
    }

    it("still rejects a call to an abstract interface method (E0005)") {
      val codes = errorCodes(
        "interface A { def g(): Int }\n" +
        "class C conforms A {\npublic:\n  def this {}\n" +
        "  def g(): Int = super.g()\n}\n")
      assert(codes == Seq("E0005"), s"expected E0005, got: $codes")
    }
  }

  describe("super.m() reaching a default through an abstract superclass (#2702)") {
    it("runs instead of throwing IncompatibleClassChangeError") {
      assert(Shell.Success(6) == run(
        "interface I {\n  def g(): Int = 5\n}\n" +
        "abstract class Base conforms I { }\n" +
        "class Child extends Base {\npublic:\n  override def g(): Int = super.g() + 1\n}\n" +
        "class Main { public: static def main(args: String[]): Int { return new Child().g() } }"))
    }

    it("runs for a Java default reached through a JDK superclass") {
      assert(Shell.Success(3L) == run(
        "import { java.util.* }\n" +
        "class L extends AbstractList[String] {\npublic:\n  def this {}\n" +
        "  def get(i: Int): String = \"x\" + i\n  def size(): Int = 3\n" +
        "  def count(): Long = super.stream().count()\n}\n" +
        "class Main { public: static def main(args: String[]): Long { return new L().count() } }"))
    }
  }

  describe("super.m() to an interface default inside a lambda") {
    it("reaches the default through the enclosing class (no VerifyError)") {
      assert(Shell.Success(8) == run(
        "interface A { def g(): Int = 7 }\n" +
        "class C conforms A {\npublic:\n  def this {}\n" +
        "  override def g(): Int { val f: () -> Int = () -> super.g() + 1; return f() }\n}\n" +
        "class Main { public: static def main(args: String[]): Int { return new C().g() } }"))
    }

    it("reaches a default through an abstract superclass (#2702)") {
      assert(Shell.Success(6) == run(
        "interface I {\n  def g(): Int = 5\n}\n" +
        "abstract class Base conforms I { }\n" +
        "class Child extends Base {\npublic:\n  def this {}\n" +
        "  override def g(): Int { val f: () -> Int = () -> super.g() + 1; return f() }\n}\n" +
        "class Main { public: static def main(args: String[]): Int { return new Child().g() } }"))
    }
  }

  describe("the most specific default wins, as in Java") {
    it("calls B's override when B conforms A") {
      assert(Shell.Success(20) == run(
        "interface A { def g(): Int = 1 }\n" +
        "interface B conforms A { def g(): Int = 2 }\n" +
        "class C conforms A, B {\npublic:\n  def this {}\n" +
        "  override def g(): Int = super.g() * 10\n}\n" +
        "class Main { public: static def main(args: String[]): Int { return new C().g() } }"))
    }
  }
}
