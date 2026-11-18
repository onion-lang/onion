package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * #3012: a superclass's private method called bare and positionally from a subclass
 * (`pim(7, 3)`) compiled, then failed at run time with IllegalAccessError. Every other
 * spelling of the same call (`this.pim(7, 3)`, named arguments, a trailing lambda) is
 * E0013 at compile time; the bare positional call now is too.
 */
class InheritedPrivateUnqualifiedCallSpec extends AbstractShellSpec {
  private def codes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(e => s"${e.errorCode.getOrElse("")}: ${e.message}")
      case _ => Seq.empty
    }
  }

  private def subclassCalling(baseMember: String, call: String): String =
    s"""
       |class A {
       |$baseMember
       |public:
       |  def this {}
       |}
       |class B extends A {
       |public:
       |  def this {}
       |  def run(): Int = $call
       |}
       |IO::println(new B().run())
       |""".stripMargin

  describe("an inherited private method called unqualified") {
    it("is E0013 for a positional instance call") {
      val msgs = codes(subclassCalling("  def pim(a: Int, b: Int): Int = a - b", "pim(7, 3)"))
      assert(msgs.exists(_.startsWith("E0013")), s"expected E0013, got: $msgs")
      assert(msgs.exists(_.contains("A.pim")), s"expected the message to name A.pim, got: $msgs")
    }

    it("is E0013 for a positional static call") {
      val msgs = codes(subclassCalling("  static def pim(a: Int): Int = a", "pim(7)"))
      assert(msgs.exists(_.startsWith("E0013")), s"expected E0013, got: $msgs")
    }

    it("is E0013 from a lambda inside the subclass") {
      val msgs = codes(subclassCalling("  def pim(a: Int): Int = a", "pim(7)").replace("def run(): Int = pim(7)", "def run(): Int { val f: () -> Int = () -> pim(7); return f() }"))
      assert(msgs.exists(_.startsWith("E0013")), s"expected E0013, got: $msgs")
    }

    // The panel's block on #3152: these three spellings still compiled and crashed.
    it("is E0013 with an untyped trailing lambda") {
      val msgs = codes(subclassCalling("  def pim(f: (Int) -> Int): Int = f(1)", "pim { x -> x }"))
      assert(msgs.exists(_.startsWith("E0013")), s"expected E0013, got: $msgs")
      assert(msgs.exists(_.contains("A.pim")), s"expected the message to name A.pim, got: $msgs")
    }

    it("is E0013 with an untyped lambda argument") {
      val msgs = codes(subclassCalling("  def pim(f: (Int) -> Int): Int = f(1)", "pim((x) -> x)"))
      assert(msgs.exists(_.startsWith("E0013")), s"expected E0013, got: $msgs")
    }

    it("is E0013 with named arguments") {
      val msgs = codes(subclassCalling("  def pim(a: Int, b: Int): Int = a - b", "pim(b = 3, a = 7)"))
      assert(msgs.exists(_.startsWith("E0013")), s"expected E0013, got: $msgs")
      assert(msgs.count(_.startsWith("E0013")) == 1, s"expected one E0013, got: $msgs")
    }
  }

  describe("calls that stay legal") {
    it("an inherited protected method called with an untyped trailing lambda runs") {
      val src = subclassCalling("protected:\n  def pim(f: (Int) -> Int): Int = f(1)", "pim { x -> x + 6 }").replace("IO::println(new B().run())", "def main(args: String[]): Int { return new B().run() }")
      assert(shell.run(src, "None", Array()) == Shell.Success(7))
    }

    it("a class's own private method called with an untyped trailing lambda and named arguments runs") {
      val src =
        """
          |class B {
          |  def pim(f: (Int) -> Int): Int = f(1)
          |  def sub(a: Int, b: Int): Int = a - b
          |public:
          |  def this {}
          |  def run(): Int = pim { x -> x + 2 } + pim((x) -> x) + sub(b = 3, a = 7)
          |}
          |def main(args: String[]): Int { return new B().run() }
          |""".stripMargin
      assert(shell.run(src, "None", Array()) == Shell.Success(8))
    }

    it("an inherited protected method called unqualified runs") {
      val src = subclassCalling("protected:\n  def pim(a: Int): Int = a", "pim(7)").replace("IO::println(new B().run())", "def main(args: String[]): Int { return new B().run() }")
      assert(shell.run(src, "None", Array()) == Shell.Success(7))
    }

    it("a class's own private method called unqualified runs") {
      val src =
        """
          |class B {
          |  def pim(a: Int, b: Int): Int = a - b
          |public:
          |  def this {}
          |  def run(): Int = pim(7, 3)
          |}
          |def main(args: String[]): Int { return new B().run() }
          |""".stripMargin
      assert(shell.run(src, "None", Array()) == Shell.Success(4))
    }

    it("a bare call skips an inherited private overload for an accessible one, as this.f(..) does") {
      val src =
        """
          |class A {
          |  def foo(a: Int): Int = 1
          |public:
          |  def this {}
          |  def foo(a: Object): Int = 2
          |}
          |class B extends A {
          |public:
          |  def this {}
          |  def run(): Int = foo(1) * 10 + this.foo(1)
          |}
          |def main(args: String[]): Int { return new B().run() }
          |""".stripMargin
      assert(shell.run(src, "None", Array()) == Shell.Success(22))
    }
  }
}
