package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A record component named like a method the record generates itself (`hashCode`,
 * `toString`) made the accessor collide with the generated method. The record compiled,
 * then failed to load with a raw `ClassFormatError` (#2391).
 */
class RecordComponentGeneratedNameSpec extends AbstractShellSpec {
  private def codes(src: String): Seq[Option[String]] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(_.errorCode)
      case _ => Seq.empty
    }
  }

  describe("a record component named like a generated method") {
    it("rejects hashCode at compile time") {
      assert(codes("record H(hashCode: Int)\nprintln(new H(3).hashCode())").contains(Some("E0010")))
    }

    it("rejects toString at compile time") {
      assert(codes("record T(toString: String)\nprintln(new T(\"x\").toString())").contains(Some("E0010")))
    }

    it("still accepts ordinary and Object-method-like names") {
      assert(codes("record W(wait: Int, hash: Int, equals: Int)\nprintln(new W(1, 2, 3).hash())").isEmpty)
    }
  }
}
