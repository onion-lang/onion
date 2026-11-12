package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * An interface method with the name and descriptor of a final `java.lang.Object` method
 * (`notify()`, `getClass()`) compiled, then failed to load with
 * `IncompatibleClassChangeError` (#2572). A class and a record already get E0039.
 */
class InterfaceFinalObjectOverrideSpec extends AbstractShellSpec {
  private def codes(src: String): Seq[Option[String]] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.toSeq.map(_.errorCode)
      case _ => Seq.empty
    }
  }

  private def body(methods: String): String = s"interface N {\n$methods\n}\nIO::println(\"x\")\n"

  describe("an interface method over a final Object method") {
    it("rejects notify() with E0039") {
      assert(codes(body("  def notify(): void")) == Seq(Some("E0039")))
    }

    it("rejects notifyAll(), wait() and wait(Long), once each") {
      val cs = codes(body("  def notifyAll(): void\n  def wait(): void\n  def wait(ms: Long): void"))
      assert(cs == Seq(Some("E0039"), Some("E0039"), Some("E0039")), cs)
    }

    it("accepts a same-named method with another descriptor") {
      assert(codes(body("  def notify(x: Int): void\n  def wait(s: String): void")).isEmpty)
    }

    it("still accepts overridable Object methods such as toString()") {
      assert(codes(body("  def toString(): String")).isEmpty)
    }
  }
}
