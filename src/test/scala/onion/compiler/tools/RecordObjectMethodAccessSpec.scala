package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A `static` or private record body method with the signature of the generated
 * `equals(Object)`, `hashCode()` or `toString()` silently dropped the generated method.
 * The record compiled, then `==` threw IncompatibleClassChangeError or IllegalAccessError,
 * and printing gave `R@80` (#2746, #2754). It is now E0010, naming the clash; a public or
 * protected instance method still replaces the generated one.
 */
class RecordObjectMethodAccessSpec extends AbstractShellSpec {
  private def errors(src: String) = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.toSeq
      case _ => Seq.empty
    }
  }

  private def codes(src: String): Seq[Option[String]] = errors(src).map(_.errorCode)

  private def record(section: String, member: String, use: String): String =
    s"""record R(name: String) {
       |$section:
       |  $member
       |}
       |$use
       |""".stripMargin

  describe("a static record method with a generated Object method's signature") {
    it("static equals(o: Object) is E0010") {
      assert(codes(record("public", "static def equals(o: Object): Boolean = true",
        "println(new R(\"a\") == new R(\"b\"))")) == Seq(Some("E0010")))
    }
    it("static toString() is E0010") {
      assert(codes(record("public", "static def toString(): String = \"s\"", "println(new R(\"a\"))")) == Seq(Some("E0010")))
    }
    it("static hashCode() is E0010, not a follow-on E0019 at the call") {
      assert(codes(record("public", "static def hashCode(): Int = 1", "println(new R(\"a\").hashCode())")) == Seq(Some("E0010")))
    }
    it("the message names the method and says to remove `static`") {
      val msg = errors(record("public", "static def toString(): String = \"s\"", "println(new R(\"a\"))")).map(_.message).mkString
      assert(msg.contains("toString()") && msg.contains("`static`"))
    }
  }

  describe("a private record method with a generated Object method's signature") {
    it("private equals(o: Object) is E0010") {
      assert(codes(record("private", "def equals(o: Object): Boolean = false",
        "println(new R(\"a\") == new R(\"a\"))")) == Seq(Some("E0010")))
    }
    it("private toString() is E0010") {
      assert(codes(record("private", "def toString(): String = \"p\"", "println(new R(\"a\"))")) == Seq(Some("E0010")))
    }
    it("private hashCode() is E0010") {
      assert(codes(record("private", "def hashCode(): Int = 1", "println(new R(\"a\"))")) == Seq(Some("E0010")))
    }
    it("the message says to move it under `public:`") {
      val msg = errors(record("private", "def toString(): String = \"p\"", "println(new R(\"a\"))")).map(_.message).mkString
      assert(msg.contains("toString()") && msg.contains("`public:`"))
    }
  }

  describe("what still compiles") {
    it("a public toString() still replaces the generated one") {
      assert(codes(record("public", "def toString(): String = \"p\"", "println(new R(\"a\"))")).isEmpty)
    }
    it("a protected toString() still replaces the generated one") {
      assert(codes(record("protected", "def toString(): String = \"p\"", "println(new R(\"a\"))")).isEmpty)
    }
    it("a private overload toString(n: Int) keeps the generated toString()") {
      assert(codes(record("private", "def toString(n: Int): String = \"p\"", "println(new R(\"a\"))")).isEmpty)
    }
    it("a static overload equals(a: R, b: R) is fine") {
      assert(codes(record("public", "static def equals(a: R, b: R): Boolean = true", "println(new R(\"a\") == new R(\"a\"))")).isEmpty)
    }
    it("a private toString() in a class is not affected") {
      val src =
        """class C {
          |private:
          |  def toString(): String = "p"
          |public:
          |  def this {}
          |}
          |println(new C() != null)
          |""".stripMargin
      assert(codes(src).isEmpty)
    }
  }
}
