package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * A record component named like a method the record generates itself (`hashCode`,
 * `toString`) made the accessor collide with the generated method. The record compiled,
 * then failed to load with a raw `ClassFormatError` (#2391).
 */
class RecordComponentGeneratedNameSpec extends AbstractShellSpec {
  private def errors(src: String) = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.toSeq
      case _ => Seq.empty
    }
  }

  private def codes(src: String): Seq[Option[String]] = errors(src).map(_.errorCode)

  describe("a record component named like a generated method") {
    it("rejects hashCode at compile time") {
      assert(codes("record H(hashCode: Int)\nprintln(new H(3).hashCode())").contains(Some("E0010")))
    }

    it("rejects toString at compile time") {
      assert(codes("record T(toString: String)\nprintln(new T(\"x\").toString())").contains(Some("E0010")))
    }

    it("rejects a nullable String toString, which erases to the same signature") {
      assert(codes("record T(toString: String?)\nprintln(new T(null))").contains(Some("E0010")))
    }

    it("reports a repeated hashCode component once per clash, not once per occurrence") {
      val cs = codes("record H(hashCode: Int, hashCode: Int)")
      assert(cs.count(_.contains("E0010")) == 1, cs)
    }

    it("says the component clashes with the generated method, not that a method is duplicated") {
      // Locale-agnostic: both the en and ja texts quote the accessor and the generated method.
      val msgs = errors("record H(hashCode: Int)").filter(_.errorCode.contains("E0010")).map(_.message)
      assert(msgs.size == 1, msgs)
      assert(msgs.head.contains("hashCode: Int") && msgs.head.contains("hashCode()"), msgs)
      assert(!msgs.head.contains("H.hashCode()"), msgs)
      val ts = errors("record T(toString: String?)").filter(_.errorCode.contains("E0010")).map(_.message)
      assert(ts.size == 1 && ts.head.contains("toString: String?") && ts.head.contains("toString()"), ts)
    }

    it("rejects toString typed by a type parameter bounded by String, which erases to String (#2522)") {
      assert(codes("record T[X extends String](toString: X)\nprintln(new T[String](\"a\"))").contains(Some("E0010")))
      assert(codes("record T[X extends String](toString: X?)").contains(Some("E0010")))
      assert(codes("record T[Y extends String, X extends Y](toString: X)").contains(Some("E0010")))
    }

    it("accepts toString typed by a type parameter that erases to something else") {
      assert(codes("record T[X](toString: X)\nprintln(new T[String](\"a\").toString())").isEmpty)
      assert(codes("record T[X extends CharSequence](toString: X)\nprintln(new T[String](\"a\"))").isEmpty)
    }

    it("accepts a component whose type gives the accessor a different signature") {
      assert(codes("record T(toString: Int)\nprintln(new T(5).toString())").isEmpty)
      assert(codes("record H(hashCode: String)\nprintln(new H(\"a\").hashCode())").isEmpty)
    }

    it("runs a record whose accessor only shares the generated method's name") {
      val src =
        """record T(toString: Int)
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    val t = new T(5)
          |    return t.toString() + " " + t
          |  }
          |}
          |""".stripMargin
      assert(shell.run(src, "None", Array()) == Shell.Success("5 T(toString=5)"))
    }

    it("still accepts ordinary and Object-method-like names") {
      assert(codes("record W(wait: Int, hash: Int, equals: Int)\nprintln(new W(1, 2, 3).hash())").isEmpty)
    }
  }
}
