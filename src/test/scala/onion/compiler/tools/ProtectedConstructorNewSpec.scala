package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * `new P()` for a protected constructor of a class in another package compiled, then the
 * class failed to load with VerifyError "Bad access to protected <init>" (#2918). Java allows
 * it only through `super(...)`, so `new` is E0013 and a subclass's `: super(...)` still works.
 */
class ProtectedConstructorNewSpec extends AbstractShellSpec {
  private def codes(src: String): Seq[Option[String]] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.toSeq.map(_.errorCode)
      case _ => Seq.empty
    }
  }

  describe("a protected constructor of a class in another package") {
    it("new inside a subclass is E0013, not a VerifyError") {
      val src =
        """import { java.io.ObjectInputStream }
          |class S extends ObjectInputStream {
          |public:
          |  def this() { }
          |  def mk(): ObjectInputStream = new ObjectInputStream()
          |}
          |println("x")
          |""".stripMargin
      assert(codes(src) == Seq(Some("E0013")))
    }
    it("the subclass's own constructor and new of it still compile") {
      val src =
        """class L extends ClassLoader {
          |public:
          |  def this() { }
          |  def again(): L = new L()
          |}
          |println("x")
          |""".stripMargin
      assert(codes(src).isEmpty)
    }
  }
}
