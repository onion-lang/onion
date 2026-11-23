package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * #3175: a subclass assigning an inherited static var by its bare name (`K = 6`, `K += 1`)
 * was E0002 "local variable K is not found", though the bare read worked. The write now
 * resolves to the same field; an inherited private static is E0014, as the read is.
 */
class InheritedStaticFieldWriteSpec extends AbstractShellSpec {
  private def codes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(e => s"${e.errorCode.getOrElse("")}: ${e.message}")
      case _ => Seq.empty
    }
  }

  private def program(access: String, body: String): String =
    s"""
       |class A {
       |$access
       |  static var K: Int = 5
       |public:
       |  def this {}
       |}
       |class B extends A {
       |public:
       |  def this {}
       |  def run(): Int { $body }
       |}
       |def main(args: String[]): Int { return new B().run() }
       |""".stripMargin

  describe("an inherited protected static var") {
    it("is assigned by its bare name") {
      assert(shell.run(program("protected:", "K = 6; return K"), "None", Array()) == Shell.Success(6))
    }
    it("takes a compound assignment") {
      assert(shell.run(program("protected:", "K += 2; return K"), "None", Array()) == Shell.Success(7))
    }
  }

  describe("an inherited public static var") {
    it("is assigned by its bare name") {
      assert(shell.run(program("public:", "K = 9; return A::K"), "None", Array()) == Shell.Success(9))
    }
  }

  describe("an inherited private static var") {
    it("is E0014, once, and never E0002") {
      val msgs = codes(program("", "K = 6; return 0"))
      assert(msgs.count(_.startsWith("E0014")) == 1, s"expected one E0014, got: $msgs")
      assert(!msgs.exists(_.startsWith("E0002")), s"unexpected E0002: $msgs")
    }
  }

  // #3202's block: a top-level `var` of the same name keeps the write, as on `main`.
  describe("a top-level var with the inherited static's name") {
    it("takes the write over an inherited interface constant (Swing's WIDTH)") {
      val src =
        """
          |import { javax.swing.JPanel }
          |var WIDTH: Int = 0
          |class P extends JPanel {
          |public:
          |  def this {}
          |  def setup(): void { WIDTH = 800 }
          |}
          |def main(args: String[]): Int { new P().setup(); return WIDTH }
          |""".stripMargin
      assert(shell.run(src, "None", Array()) == Shell.Success(800))
    }
    it("takes the write over an inherited public static final (Thread.MAX_PRIORITY)") {
      val src =
        """
          |var MAX_PRIORITY: Int = 0
          |class T extends Thread {
          |public:
          |  def this {}
          |  def setup(): void { MAX_PRIORITY = 42 }
          |}
          |def main(args: String[]): Int { new T().setup(); return MAX_PRIORITY }
          |""".stripMargin
      assert(shell.run(src, "None", Array()) == Shell.Success(42))
    }
    it("takes the write over an inherited public static var") {
      val src =
        """
          |var count: Int = 0
          |class A {
          |public:
          |  static var count: Int = 0
          |  def this {}
          |}
          |class B extends A {
          |public:
          |  def this {}
          |  def setup(): void { count = 7 }
          |}
          |def main(args: String[]): Int { new B().setup(); return count * 10 + A::count }
          |""".stripMargin
      assert(shell.run(src, "None", Array()) == Shell.Success(70))
    }
    it("takes the write over an inherited private static var") {
      val src =
        """
          |var K: Int = 0
          |class A {
          |  static var K: Int = 5
          |public:
          |  def this {}
          |}
          |class B extends A {
          |public:
          |  def this {}
          |  def setup(): void { K = 7 }
          |}
          |def main(args: String[]): Int { new B().setup(); return K }
          |""".stripMargin
      assert(shell.run(src, "None", Array()) == Shell.Success(7))
    }
  }

  describe("a name that is no field") {
    it("is still E0002") {
      val msgs = codes(program("protected:", "Z = 6; return 0"))
      assert(msgs.exists(_.startsWith("E0002")), s"expected E0002, got: $msgs")
    }
  }
}
