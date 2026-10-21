package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * An ADT enum's `case` line takes no body, `derive!` or `from` clause (RFC #2250,
 * panel option 3). The fix is a standalone `record ... conforms TheEnum` beside the
 * enum: it joins the sealed set, so `select` exhaustiveness (E0042) still applies,
 * and it carries every record clause. Before, the parser said only
 * `Encountered "{", but expecting "}"`, which reads as "close the enum".
 *
 * Assertions use the hint's literal `record Name(fields)` snippet and error codes,
 * which are the same in both locales.
 */
class EnumCaseBodyHintSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.enum_case_body"
  private val en = MessageBundles.english
  private val ja = MessageBundles.japanese

  private def compile(src: String): Either[String, Unit] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) =>
        Left(errors.map(e => e.errorCode.fold("")(c => s"[$c] ") + e.message).mkString("\n"))
      case _ => Right(())
    }
  }

  private def errorsOf(src: String): String = compile(src) match {
    case Left(msgs) => msgs
    case Right(_) => fail(s"expected a compile error for:\n$src")
  }

  it("resolves in the English bundle") {
    assert(en.getString(key).contains("Hint:"))
  }

  it("resolves in the Japanese bundle with actual Japanese text") {
    val text = ja.getString(key)
    assert(text.contains("ヒント"), s"expected a Japanese hint, got: $text")
    assert(text != en.getString(key))
  }

  describe("the hint fires") {
    it("for a `{ ... }` body after a case with fields") {
      val msgs = errorsOf(
        """enum Shape {
          |  case Circle(radius: Double) {
          |    law positive(r: Double) { r > 0.0 }
          |  }
          |  case Square(side: Double)
          |}
          |""".stripMargin)
      assert(msgs.contains("record Circle(radius: Double) conforms"), msgs)
    }

    it("for `derive!` after a case") {
      val msgs = errorsOf(
        """enum Msg {
          |  case Ping(id: Int) derive!(Json)
          |  case Pong(id: Int)
          |}
          |""".stripMargin)
      assert(msgs.contains("record Ping(id: Int) conforms"), msgs)
    }

    it("for `from re\"...\"` after a case") {
      val msgs = errorsOf(
        """enum Log {
          |  case Access(path: String, status: Int) from re"(\S+) (\d+)"
          |  case Other
          |}
          |""".stripMargin)
      assert(msgs.contains("record Access(path: String, status: Int) conforms"), msgs)
    }

    it("for a body after a field-less case") {
      val msgs = errorsOf(
        """enum T {
          |  case Leaf {
          |    def x(): Int = 1
          |  }
          |}
          |""".stripMargin)
      assert(msgs.contains("record Leaf() conforms"), msgs)
    }

    it("for the second case on a `;`-separated line, naming that case") {
      val msgs = errorsOf("enum Msg { case Ping(id: Int); case Pong(id: Int) derive!(Json) }\n")
      assert(msgs.contains("record Pong(id: Int) conforms"), msgs)
      assert(!msgs.contains("record Ping"), msgs)
    }
  }

  describe("the hint stays out of") {
    it("a homogeneous enum's missing comma") {
      val msgs = errorsOf(
        """enum Planet(mass: Double) {
          |  MERCURY(3.3)
          |  VENUS(4.87)
          |}
          |""".stripMargin)
      assert(!msgs.contains("conforms"), msgs)
      assert(msgs.contains(", VENUS("), msgs)
    }
  }

  describe("the record the hint suggests") {
    val enumAndRecord =
      """enum Msg { case Ping(id: Int); case Pong(id: Int) }
        |record Pang(id: Int) derive!(Json) conforms Msg {
        |  example { new Pang(3).id() == 3 }
        |}
        |""".stripMargin

    it("counts for `select` exhaustiveness (E0042)") {
      val msgs = errorsOf(enumAndRecord +
        """def f(m: Msg): String = select m { case p is Ping: "i"; case p is Pong: "o" }
          |""".stripMargin)
      assert(msgs.contains("E0042"), msgs)
      assert(msgs.contains("Pang"), msgs)
    }

    it("compiles with its clauses when `select` lists it") {
      val result = compile(enumAndRecord +
        """def f(m: Msg): String = select m { case p is Ping: "i"; case p is Pong: "o"; case p is Pang: Pang::toJson(p) }
          |""".stripMargin)
      assert(result == Right(()), result.left.getOrElse(""))
    }

    it("runs its `example` clause") {
      val msgs = errorsOf(
        """enum Msg { case Ping(id: Int) }
          |record Pang(id: Int) conforms Msg {
          |  example { new Pang(3).id() == 4 }
          |}
          |""".stripMargin)
      assert(msgs.contains("E0065"), msgs)
    }
  }
}
