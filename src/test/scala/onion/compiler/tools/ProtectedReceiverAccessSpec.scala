package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * A protected instance member reached from a subclass of its owner was accepted whatever
 * the receiver was, so `new Object().clone()` compiled and then failed to load with
 * VerifyError "Bad access to protected data" (#2900). Java (JLS 6.6.2.1) and the JVM
 * allow it only on a receiver of the accessing class or a subclass of it. Such a call
 * is now E0013 (a field, E0014); `this`, an unqualified call and a receiver of the
 * subclass's own type still compile, as does a static protected member.
 */
class ProtectedReceiverAccessSpec extends AbstractShellSpec {
  private def codes(src: String): Seq[Option[String]] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.toSeq.map(_.errorCode)
      case _ => Seq.empty
    }
  }

  describe("a protected member on a receiver that is not the accessing class") {
    it("new Object().clone() in a script is E0013, not a VerifyError") {
      assert(codes("val o = new Object()\no.clone()\nprintln(\"done\")\n") == Seq(Some("E0013")))
    }
    it("a protected method on a superclass-typed receiver in a subclass is E0013") {
      val src =
        """import { java.util.ArrayList }
          |class MyList extends ArrayList[String] {
          |public:
          |  def cut(other: ArrayList[String]): void { other.removeRange(0, 1) }
          |}
          |println("x")
          |""".stripMargin
      assert(codes(src) == Seq(Some("E0013")))
    }
    it("a protected field on a superclass-typed receiver in a subclass is E0014") {
      val src =
        """import { java.util.ArrayList }
          |class MyList extends ArrayList[String] {
          |public:
          |  def peek(other: ArrayList[String]): Int = other.modCount
          |}
          |println("x")
          |""".stripMargin
      assert(codes(src) == Seq(Some("E0014")))
    }
  }

  describe("what still compiles and runs") {
    it("this, an unqualified call and a receiver of the subclass's own type") {
      val src =
        """import { java.util.ArrayList }
          |class MyList extends ArrayList[String] {
          |public:
          |  def cutSelf(): void { removeRange(0, 1) }
          |  def cutThis(): void { this.removeRange(0, 1) }
          |  def cutOther(other: MyList): void { other.removeRange(0, 1) }
          |  def mods(other: MyList): Int = other.modCount - this.modCount
          |}
          |def main(args: String[]): Int {
          |  val a = new MyList()
          |  a.add("1"); a.add("2"); a.add("3"); a.add("4")
          |  val b = new MyList()
          |  b.add("x"); b.add("y")
          |  a.cutSelf()
          |  a.cutThis()
          |  a.cutOther(b)
          |  a.mods(b)
          |  return a.size * 10 + b.size
          |}
          |""".stripMargin
      assert(shell.run(src, "None", Array()) == Shell.Success(21))
    }
    it("a protected member of an Onion class on a sibling receiver in the same package") {
      val src =
        """class Base {
          |protected:
          |  def secret(): Int = 7
          |}
          |class Sub extends Base {
          |public:
          |  def peek(b: Base): Int = b.secret()
          |}
          |def main(args: String[]): Int { return new Sub().peek(new Base()) }
          |""".stripMargin
      assert(shell.run(src, "None", Array()) == Shell.Success(7))
    }
  }
}
