package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A record body method named and parameterised like an overridable `Object` method but with an
 * incompatible return (`def toString(): Int`) compiled and dropped the generated method, or
 * answered it two ways (#2713). A class gets E0104 for it; so does a record.
 */
class RecordObjectReturnMismatchSpec extends AbstractShellSpec {
  private def codes(src: String): Seq[Option[String]] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.toSeq.map(_.errorCode)
      case _ => Seq.empty
    }
  }

  private def body(methods: String): String =
    s"record R(name: String) {\npublic:\n$methods\n}\nIO::println(new R(\"a\"))\n"

  describe("a record body method over a non-final Object method") {
    it("rejects toString(): Int and hashCode(): Long with E0104") {
      assert(codes(body("  def toString(): Int = 5")) == Seq(Some("E0104")))
      assert(codes(body("  def hashCode(): Long = 5L")) == Seq(Some("E0104")))
    }

    it("rejects equals(Object): Int with E0104") {
      assert(codes(body("  def equals(o: Object): Int = 1")) == Seq(Some("E0104")))
    }

    it("still accepts a matching toString and a different-parameter overload") {
      assert(codes(body("  def toString(): String = \"r\"")).isEmpty)
      assert(codes(body("  def equals(o: Object, n: Int): Int = 1")).isEmpty)
      assert(codes(body("  def toString(n: Int): Int = n")).isEmpty)
    }
  }
}
