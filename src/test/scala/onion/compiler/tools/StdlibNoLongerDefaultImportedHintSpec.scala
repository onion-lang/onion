package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * v0.10 narrowed the default static import set to pure classes (#360): `System`,
 * `Runtime`, `Files`, `Http` and `DateTime` are no longer imported into every file, so
 * a bare `readText(p)` / `get(url)` / `now()` / `exit(1)` stops resolving -- exactly
 * the gotcha CLAUDE.md documents under "Tools, capabilities and effects". Without a
 * hint, the failure is an ordinary METHOD_NOT_FOUND naming the synthetic top-level (or
 * enclosing) class as if the call were simply misspelled, with nothing connecting it
 * back to the import change. `SemanticErrorReporter.reportMethodNotFound` now points at
 * the qualified form whenever the name and argument count exactly match one of those
 * five classes' real static methods -- but only for a genuinely unqualified call, since
 * that is the one situation where every other resolution path has already failed and a
 * name+arity match can only be this stdlib method.
 */
class StdlibNoLongerDefaultImportedHintSpec extends AbstractShellSpec {
  private def errors(src: String) = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs
      case _ => Seq.empty
    }
  }

  private def messagesOf(src: String): String = errors(src).map(_.message).mkString("\n")
  private def codesOf(src: String): Seq[String] = errors(src).flatMap(_.code)

  describe("bare call to a former default import") {
    it("hints at Files::readText for a bare one-argument readText call") {
      val src =
        """
          |def main(): void {
          |  val x = readText("f.txt")
          |  IO::println(x)
          |}
          |""".stripMargin
      assert(codesOf(src).contains("E0005"), s"expected E0005 (method not found), got: ${codesOf(src)}")
      val msgs = messagesOf(src)
      assert(msgs.contains("Files::readText"), s"expected a hint naming Files::readText, got: $msgs")
    }

    it("hints at DateTime::now for a bare zero-argument now call") {
      val src =
        """
          |def main(): void {
          |  val t = now()
          |  IO::println(t)
          |}
          |""".stripMargin
      val msgs = messagesOf(src)
      assert(msgs.contains("DateTime::now"), s"expected a hint naming DateTime::now, got: $msgs")
    }

    it("hints at System::exit for a bare one-argument exit call") {
      val src =
        """
          |def main(): void {
          |  exit(1)
          |}
          |""".stripMargin
      val msgs = messagesOf(src)
      assert(msgs.contains("System::exit"), s"expected a hint naming System::exit, got: $msgs")
    }

    it("fires inside an ordinary class method, not only at the top level") {
      val src =
        """
          |class Greeter {
          |public:
          |  def greet(): void {
          |    val body = readText("f.txt")
          |    IO::println(body)
          |  }
          |}
          |def main(): void {
          |  new Greeter().greet()
          |}
          |""".stripMargin
      val msgs = messagesOf(src)
      assert(msgs.contains("Files::readText"), s"expected a hint naming Files::readText, got: $msgs")
    }

    it("does not fire for an unrelated unqualified not-found call") {
      val src =
        """
          |def main(): void {
          |  frobnicate(1)
          |}
          |""".stripMargin
      assert(codesOf(src).contains("E0005"), s"expected E0005 (method not found), got: ${codesOf(src)}")
      val msgs = messagesOf(src)
      assert(!msgs.contains("no longer a default import"), s"hint should not fire for an unrelated name, got: $msgs")
    }

    it("does not fire for a qualified call on an unrelated receiver, even with a matching name") {
      val src =
        """
          |class Box {
          |public:
          |  val value: Int
          |  def this(value: Int) { self.value = value }
          |}
          |def main(): void {
          |  val b = new Box(1)
          |  b.get()
          |}
          |""".stripMargin
      assert(codesOf(src).contains("E0005"), s"expected E0005 (method not found), got: ${codesOf(src)}")
      val msgs = messagesOf(src)
      assert(!msgs.contains("no longer a default import"), s"hint should not fire for a qualified call, got: $msgs")
    }
  }
}
