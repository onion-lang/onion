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

  describe("a name that is no field") {
    it("is still E0002") {
      val msgs = codes(program("protected:", "Z = 6; return 0"))
      assert(msgs.exists(_.startsWith("E0002")), s"expected E0002, got: $msgs")
    }
  }
}
