package onion.compiler

import java.io.StringReader
import java.util.Locale
import org.scalatest.funspec.AnyFunSpec

/**
 * The contract diagnostics E0106 and E0107 (RFC #2557) build part of their text from the
 * problem found: why a clause is not pure, and where an `old(...)` stood. That part used to be
 * an English sentence passed in as an argument, so a Japanese message carried English words.
 * The panel reproduced it.
 *
 * The message language is fixed when the JVM starts (`-Duser.language`), not by
 * `Locale.setDefault` afterwards, so each spec checks the language it is run in. CI runs the
 * suite in both, which is what makes this a check in both directions.
 */
class ContractMessageI18nSpec extends AnyFunSpec {

  private val japanese = Locale.getDefault.getLanguage == "ja"

  private def messages(source: String, code: String): String = {
    val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 10, checkLaws = false, contracts = ContractMode.Check)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(source), "C.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.filter(_.errorCode.contains(code)).map(_.message).mkString("\n")
      case _                                => ""
    }
  }

  private val writes =
    """import { java.util.* }
      |class A {
      |public:
      |  def f(xs: List[Int]): Int requires xs.add(1) { return 0 }
      |}""".stripMargin

  private val io =
    """class A {
      |public:
      |  def f(p: String): Int requires Files::exists(p) { return 0 }
      |}""".stripMargin

  private val oldInRequires =
    """class A {
      |  var n: Int
      |public:
      |  def f(): void requires old(n) > 0 { }
      |}""".stripMargin

  /** Phrases the English message builds from its keys; none may appear in the Japanese message. */
  private val englishPhrases =
    Seq("writes to memory", "has the effect", "effect table", "is the value on entry", "in a `requires`", "in an `invariant`")

  private def assertLanguage(msg: String, japaneseWord: String, englishWord: String): Unit = {
    assert(msg.nonEmpty, "the diagnostic was not reported")
    if (japanese) {
      assert(msg.contains(japaneseWord), s"expected '$japaneseWord' in: $msg")
      englishPhrases.foreach(p => assert(!msg.contains(p), s"English phrase '$p' leaked into the Japanese message: $msg"))
    } else {
      assert(msg.contains(englishWord), s"expected '$englishWord' in: $msg")
      assert(!msg.exists(c => c >= '\u3040' && c <= '\u30ff' || c >= '\u4e00' && c <= '\u9fff'),
        s"Japanese text in the English message: $msg")
    }
  }

  describe("E0106 (a contract that is not pure)") {
    it("names a memory write in the language it is run in") {
      assertLanguage(messages(writes, "E0106"), "メモリに書き込み", "writes to memory")
    }

    it("names an outside effect, keeping the effect name as an identifier") {
      val msg = messages(io, "E0106")
      assertLanguage(msg, "という効果を持ちます", "has the effect")
      assert(msg.contains("read"), msg)
    }
  }

  describe("E0107 (old(...) out of place)") {
    it("says where an old(...) stood, in the language it is run in") {
      assertLanguage(messages(oldInRequires, "E0107"), "`requires` の中", "in a `requires`")
    }
  }
}
