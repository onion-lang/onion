package onion.compiler

import java.io.StringReader
import java.util.Locale
import org.scalatest.funspec.AnyFunSpec

/**
 * E0113 (#2687): a predicate with parameters is sampled, so its message must not say it has
 * none, and the reason (`returned false`, `threw ...`) is part of the sentence in each language.
 * The message language is fixed when the JVM starts, so each run checks the language it is run in.
 */
class PredicateMessageSpec extends AnyFunSpec {
  private val japanese = Locale.getDefault.getLanguage == "ja"

  private def e0113(source: String): String = {
    val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 10, contracts = ContractMode.Check)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(source), "C.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.filter(_.errorCode.contains("E0113")).map(_.message).mkString("\n")
      case _                                => ""
    }
  }

  private val sampled = "def pos(x: Int): Boolean\n  ensures result\n  = x > 0\nIO::println(\"x\")\n"
  private val closed = "def sums(): Boolean\n  ensures result\n  = 1 + 1 == 3\n"
  private val throwing = "def boom(): Boolean\n  ensures result\n  = \"a\".charAt(5) == 'a'\n"

  describe("E0113") {
    it("does not tell a sampled predicate that predicates have no parameters") {
      val msg = e0113(sampled)
      assert(msg.nonEmpty, "E0113 was not reported")
      assert(!msg.contains("no parameters"), msg)
      assert(!msg.contains("引数がなく"), msg)
      assert(msg.contains("--contract-seed"), msg)
    }

    it("keeps the closed-predicate wording for a predicate with no parameters") {
      val msg = e0113(closed)
      if (japanese) assert(msg.contains("閉じた述語"), msg) else assert(msg.contains("closed predicate"), msg)
    }

    it("writes the reason in the language it is run in") {
      val msgs = Seq(e0113(sampled), e0113(closed), e0113(throwing))
      msgs.foreach(m => assert(m.nonEmpty, "E0113 was not reported"))
      if (japanese) msgs.foreach { m =>
        Seq("returned false", "threw ", "for (").foreach(p => assert(!m.contains(p), s"English '$p' leaked into: $m"))
      }
      else msgs.foreach(m => assert(!m.exists(c => c >= '぀' && c <= 'ヿ' || c >= '一' && c <= '鿿'), m))
    }

    it("reports nothing for a true predicate whose sample's toString throws (#2752)") {
      val src = "record Pt(x: Int, y: Int) {\npublic:\n  def toString(): String = { throw new IllegalStateException(\"ts\") }\n}\n" +
        "def ok(p: Pt): Boolean\n  ensures result\n  = true\nIO::println(\"x\")\n"
      assert(e0113(src) == "")
    }

    it("says an unsampleable parameter was not run, not that the predicate did not hold (#2752)") {
      val msg = e0113("def un(t: Thread): Boolean\n  ensures result\n  = true\nIO::println(\"x\")\n")
      assert(msg.nonEmpty, "E0113 was not reported")
      assert(msg.contains("Thread"), msg)
      assert(!msg.contains("did not hold") && !msg.contains("成り立ちません"), msg)
      assert(!msg.contains("。）。"), msg)
    }
  }
}
