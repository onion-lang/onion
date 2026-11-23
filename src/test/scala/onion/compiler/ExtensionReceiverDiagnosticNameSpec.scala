package onion.compiler

import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/** E0077 names an extension call by the receiver the block declared (#3009):
  * `List[Int].shout`, not `List.int.shout`; a real `_` in a type name stays. */
class ExtensionReceiverDiagnosticNameSpec extends AnyFunSpec {

  private def e0077(src: String): String =
    new OnionCompiler(new CompilerConfig(List("."), null, "UTF-8", "", 10))
      .compile(Seq(new StreamInputSource(() => new StringReader(src), "main.on"))) match {
      case CompilationOutcome.Failure(errs) =>
        errs.filter(_.errorCode.contains("E0077")).map(_.message).mkString("\n")
      case other => fail(s"expected a compile failure, got $other")
    }

  describe("E0077 for an extension method") {
    it("names a generic receiver as written") {
      val msg = e0077(
        """import { java.util.List; }
          |extension List[Int] {
          |  def shout(): Int { IO::println("x"); return 1 }
          |}
          |tool a(): void { [1].shout() }
          |""".stripMargin)
      assert(msg.contains("List[Int].shout"), msg)
      assert(!msg.contains("List.int"), msg)
    }

    it("keeps an underscore in the receiver's name") {
      val msg = e0077(
        """record Big_Box(v: Int)
          |extension Big_Box {
          |  def yell(): Int { IO::println("y"); return 1 }
          |}
          |tool b(): void { new Big_Box(1).yell() }
          |""".stripMargin)
      assert(msg.contains("Big_Box.yell"), msg)
      assert(!msg.contains("Big.Box"), msg)
    }
  }
}
