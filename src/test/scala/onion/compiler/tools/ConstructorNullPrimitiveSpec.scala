package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/** `null` never fills a primitive constructor parameter (#2828): it is a compile
  * error, as for a method call, not a run-time null-value crash. */
class ConstructorNullPrimitiveSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  private val cls =
    """
      |class A {
      |public:
      |  def this(n: Int) { println("I" + n) }
      |}
    """.stripMargin

  describe("null passed to a primitive constructor parameter") {
    it("is a compile error") {
      assert(Shell.Failure(-1) == shell.run(cls + "\nnew A(null)\n", "None", Array()))
    }
    it("is a compile error through `extends A(null)`") {
      assert(Shell.Failure(-1) == shell.run(cls + "\nclass B extends A(null)\nnew B()\n", "None", Array()))
    }
    it("is a compile error through `: this(null)`") {
      val src = cls.replace("def this(n: Int) { println(\"I\" + n) }",
        "def this(n: Int) { println(\"I\" + n) }\n  def this : this(null) { }")
      assert(Shell.Failure(-1) == shell.run(src + "\nnew A()\n", "None", Array()))
    }
    it("picks the nullable overload for `extends A(null)`, as `new A(null)` does") {
      val src =
        """
          |class A {
          |public:
          |  def this(s: String?, b: Int = 2) { println("S" + s + b) }
          |  def this(n: Int) { println("I" + n) }
          |}
          |class B extends A(null)
          |def main(args: String[]): Int { new B(); return 0 }
        """.stripMargin
      assert(Shell.Success(0) == shell.run(src, "None", Array()))
    }
    it("still accepts an Int argument") {
      assert(Shell.Success(0) == shell.run(cls + "\ndef main(args: String[]): Int { new A(1); return 0 }\n", "None", Array()))
    }
    it("still accepts null for a nullable parameter") {
      assert(Shell.Success(0) == shell.run(
        """
          |class B {
          |public:
          |  def this(s: String?) { println("B" + s) }
          |}
          |def main(args: String[]): Int { new B(null); return 0 }
        """.stripMargin, "None", Array()))
    }
  }

  // The constructor rule must not reach method calls: their argument check names
  // the `null` itself (E0000), not a generic "no applicable method" (E0005).
  describe("null passed to a primitive method parameter") {
    it("is E0000 for a top-level function") {
      assert(errorCodes("def f(n: Int): void { println(n) }\nf(null)\n") == Seq("E0000"))
    }
    it("is E0000 for an instance method") {
      val src = cls.replace("def this(n: Int) { println(\"I\" + n) }",
        "def this(n: Int) { }\n  def m(n: Int): void { println(n) }")
      assert(errorCodes(src + "\nnew A(1).m(null)\n") == Seq("E0000"))
    }
    it("is E0000 for the null among several arguments") {
      assert(errorCodes("def h(n: Int, s: String): void { println(s) }\nh(null, \"x\")\n") == Seq("E0000"))
    }
  }
}
