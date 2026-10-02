package onion.compiler

import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * A record derivation the compiler rejects (E0059/E0060/E0061/E0062/E0076) used to drop
 * the synthesized accessor entirely, so every call site then failed again with E0005
 * "Summary.doc() is not found" -- one real error buried under a cascade. The accessor's
 * signature is now registered anyway (its body is never typed or emitted), so the
 * original error stands alone.
 *
 * Assertions use error codes and the source's own identifiers only, so they hold in
 * both locales.
 */
class RejectedRecordDerivationCascadeSpec extends AnyFunSpec {

  private def errors(src: String): Seq[CompileError] =
    new OnionCompiler(new CompilerConfig(List("."), null, "UTF-8", "", 10))
      .compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs
      case other                            => fail(s"expected a compile failure, got $other")
    }

  private def codes(errs: Seq[CompileError]): Seq[String] = errs.flatMap(_.errorCode)

  private def assertOnly(src: String, code: String): Unit = {
    val errs = errors(src)
    val cs = codes(errs)
    assert(cs.contains(code), errs.map(_.message).mkString("\n"))
    assert(!cs.contains("E0005"), s"cascading E0005:\n${errs.map(_.message).mkString("\n")}")
  }

  describe("shape clauses") {
    it("a json shape over an unsupported component (the dogfood report) is one E0061") {
      assertOnly(
        """record Summary(title: String, xs: Map[String, String]) {
          |  shape doc = json
          |}
          |val s = Summary::doc().parse("{}").get()
          |println(s.title())
          |println(Summary::doc())
          |""".stripMargin, "E0061")
    }

    it("a regex shape with the wrong group count is one E0060") {
      assertOnly(
        """record Q(a: String, b: String) {
          |  shape s = re"(\S+)"
          |}
          |println(Q::s().parse("x"))
          |""".stripMargin, "E0060")
    }

    it("an unknown shape format is one E0076") {
      assertOnly(
        """record T(a: String, b: Int) {
          |  shape doc = xml
          |}
          |println(T::doc().parse("x"))
          |""".stripMargin, "E0076")
    }
  }

  describe("from re\"...\"") {
    it("an unsupported component leaves parse/parseAll callable: one E0061") {
      assertOnly(
        """record Access(at: java.util.Date, path: String) from re"(\S+) (\S+)"
          |val a = Access::parse("x y")
          |println(a?.path())
          |println(Access::parseAll("x y"))
          |""".stripMargin, "E0061")
    }
  }

  describe("derive!") {
    it("an unsupported component leaves fromJson/toJson callable: one E0062") {
      assertOnly(
        """record P(xs: List[String], n: Int) derive!(Json)
          |val p = P::fromJson("{}")
          |println(P::toJson(p!!))
          |""".stripMargin, "E0062")
    }
  }

  describe("what is not suppressed") {
    it("a call to a name the record never declares is still E0005") {
      val errs = errors(
        """record Summary(title: String, xs: Map[String, String]) {
          |  shape doc = json
          |}
          |println(Summary::dco())
          |""".stripMargin)
      assert(codes(errs).contains("E0061"))
      assert(codes(errs).contains("E0005"), errs.map(_.message).mkString("\n"))
    }

    it("a well-formed shape still compiles and runs its accessor") {
      val outcome = new OnionCompiler(new CompilerConfig(List("."), null, "UTF-8", "", 10))
        .compile(Seq(new StreamInputSource(() => new StringReader(
          """record Pt(x: Int, y: Int) {
            |  shape doc = json
            |}
            |println(Pt::doc().parse("{\"x\": 1, \"y\": 2}").get().y())
            |""".stripMargin), "test.on")))
      assert(outcome.isInstanceOf[CompilationOutcome.Success], outcome.toString)
    }
  }
}
