package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * A record body method that overrides a final `java.lang.Object` method (`notify()`,
 * `getClass()`) compiled, then the class failed to load with
 * `IncompatibleClassChangeError` (#2533). A class already gets E0039 for the same method.
 */
class RecordFinalObjectOverrideSpec extends AbstractShellSpec {
  private def codes(src: String): Seq[Option[String]] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.toSeq.map(_.errorCode)
      case _ => Seq.empty
    }
  }

  private def body(methods: String): String =
    s"record R(x: Int) {\npublic:\n$methods\n}\nIO::println(new R(1))\n"

  describe("a record body method over a final Object method") {
    it("rejects notify() with E0039") {
      assert(codes(body("  def notify(): void { }")) == Seq(Some("E0039")))
    }

    it("rejects notifyAll() and wait() with E0039, once each") {
      val cs = codes(body("  def notifyAll(): void { }\n  def wait(): void { }"))
      assert(cs == Seq(Some("E0039"), Some("E0039")), cs)
    }

    it("rejects wait(ms: Long) and getClass(): Class with E0039") {
      assert(codes(body("  def wait(ms: Long): void { }")) == Seq(Some("E0039")))
      assert(codes(body("  def getClass(): Class[String]? = null")).contains(Some("E0039")))
    }

    it("rejects an overload named like a component when it matches a final Object method") {
      val cs = codes("record R(wait: Int) {\npublic:\n  def wait(ms: Long): void { }\n}\nIO::println(new R(1))\n")
      assert(cs == Seq(Some("E0039")), cs)
      val cs2 = codes("record R(wait: Int, notify: String) {\npublic:\n  def wait(ms: Long, nanos: Int): void { }\n}\nIO::println(new R(1, \"n\"))\n")
      assert(cs2 == Seq(Some("E0039")), cs2)
    }

    it("still runs a record whose same-named method has another JVM descriptor") {
      val src =
        """record R(x: Int) {
          |public:
          |  def notify(): Int = 5
          |  def wait(s: String): String = s
          |  def hashCode(): Int = 7
          |}
          |IO::println(new R(1).notify() + new R(1).wait("w") + new R(1).hashCode())
          |""".stripMargin
      assert(codes(src).isEmpty)
      assert(shell.run(src, "RecordObjectNames.on", Array()) != Shell.Failure(-1))
    }

    it("still runs a record with components named notify and wait") {
      val src = "record R(notify: Int, wait: String)\nval r = new R(3, \"w\")\nIO::println(r.notify() + r.wait())\n"
      assert(shell.run(src, "RecordObjectComponents.on", Array()) != Shell.Failure(-1))
    }
  }
}
