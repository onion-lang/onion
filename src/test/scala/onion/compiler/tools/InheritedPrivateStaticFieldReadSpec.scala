package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * #3155: a subclass reading a superclass's private static field by its bare name (`K`)
 * compiled, then failed at run time with IllegalAccessError. `A::K` was already E0014;
 * the bare read now is too.
 */
class InheritedPrivateStaticFieldReadSpec extends AbstractShellSpec {
  private def codes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(e => s"${e.errorCode.getOrElse("")}: ${e.message}")
      case _ => Seq.empty
    }
  }

  private def subclassReading(baseField: String, read: String): String =
    s"""
       |class A {
       |$baseField
       |public:
       |  def this {}
       |}
       |class B extends A {
       |public:
       |  def this {}
       |  def run(): Int = $read
       |}
       |def main(args: String[]): Int { return new B().run() }
       |""".stripMargin

  describe("an inherited private static field read by its bare name") {
    it("is E0014, once") {
      val msgs = codes(subclassReading("  static val K: Int = 5", "K"))
      assert(msgs.count(_.startsWith("E0014")) == 1, s"expected one E0014, got: $msgs")
      assert(msgs.size == 1, s"expected no other error, got: $msgs")
    }

    it("is E0014 inside an expression and a lambda") {
      val e1 = codes(subclassReading("  static val K: Int = 5", "K + 1"))
      assert(e1.exists(_.startsWith("E0014")), s"expected E0014, got: $e1")
      val e2 = codes(subclassReading("  static val K: Int = 5", "0").replace("def run(): Int = 0", "def run(): Int { val f: () -> Int = () -> K; return f() }"))
      assert(e2.exists(_.startsWith("E0014")), s"expected E0014, got: $e2")
    }
  }

  describe("reads that stay legal") {
    it("an inherited protected static field read by its bare name runs") {
      val src = subclassReading("protected:\n  static val K: Int = 5", "K")
      assert(shell.run(src, "None", Array()) == Shell.Success(5))
    }

    it("an inherited public static field read by its bare name runs") {
      val src = subclassReading("public:\n  static val K: Int = 5", "K")
      assert(shell.run(src, "None", Array()) == Shell.Success(5))
    }

    it("a class's own private static field read by its bare name runs") {
      val src =
        """
          |class B {
          |  static val K: Int = 5
          |public:
          |  def this {}
          |  def run(): Int = K
          |}
          |def main(args: String[]): Int { return new B().run() }
          |""".stripMargin
      assert(shell.run(src, "None", Array()) == Shell.Success(5))
    }
  }
}
