package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A class member with no access section (or one under `private:`) is private to its
 * own class -- the default a newcomer coming from Java/Scala/Kotlin, where an
 * unmarked member is usually public or package-visible, does not expect. E0013
 * (method/constructor) and E0014 (field) reported only "is not accessible", with no
 * pointer to the fix (`public:`/`protected:`), so the newcomer has no way to tell a
 * genuine design choice from "you forgot a section marker".
 */
class PrivateAccessHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(e => s"${e.errorCode.getOrElse("")}: ${e.message}")
      case _ => Seq.empty
    }
  }

  describe("a constructor with no access section") {
    it("E0013 hints at adding a `public:` section") {
      val msgs = messages(
        """
          |class Box {
          |  val v: Int
          |  def this(v: Int) { this.v = v }
          |}
          |val b: Box = new Box(1)
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("E0013"), s"expected E0013, got: $msgs")
      assert(msgs.contains("public:"), s"expected a hint mentioning `public:`, got: $msgs")
    }
  }

  describe("a field with no access section") {
    it("E0014 hints at adding a `public:` section") {
      val msgs = messages(
        """
          |class Box {
          |  val v: Int
          |public:
          |  def this(v: Int) { this.v = v }
          |}
          |val b: Box = new Box(1)
          |IO::println(b.v)
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("E0014"), s"expected E0014, got: $msgs")
      assert(msgs.contains("public:"), s"expected a hint mentioning `public:`, got: $msgs")
    }
  }

  describe("a constructor correctly placed under `public:`") {
    it("does not fire the private-access hint") {
      val msgs = messages(
        """
          |class Box {
          |  val v: Int
          |public:
          |  def this(v: Int) { this.v = v }
          |}
          |val b: Box = new Box(1)
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.isEmpty, s"expected no errors, got: $msgs")
    }
  }
}
