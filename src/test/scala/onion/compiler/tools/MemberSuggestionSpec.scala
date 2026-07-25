package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * A member that is not found should point at the member that is: reaching for
 * another language's spelling (`xs.length` on a List) or mixing up a field with
 * a record-style accessor (`p.name()`) are the everyday mistakes, and the
 * compiler already knows the answer in both cases.
 *
 * Assertions use the suggested *identifier* rather than the surrounding
 * sentence: error messages are bilingual (errorMessage.properties /
 * errorMessage_ja.properties) and resolve from the JVM default locale, so
 * asserting on prose would pass locally and fail in CI's English locale (or
 * vice versa).
 */
class MemberSuggestionSpec extends AnyFunSpec {

  private def errors(source: String): Seq[String] =
    new OnionCompiler(CompilerConfig(Seq("."), null, "UTF-8", "", 10))
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "S.on")))
      .allErrors
      .map(_.message)
      .toSeq

  describe("a not-found member suggests the member that exists") {
    it("suggests size for length on a collection") {
      // 6 edit distances apart, so this can only come from the alias table.
      val messages = errors("val xs = [1, 2, 3]\nprintln(xs.length)\n")
      assert(messages.nonEmpty)
      assert(messages.exists(_.contains("size")), messages.mkString("; "))
    }

    it("suggests dropping the parentheses when the member is a field") {
      val messages = errors(
        """
          |class P { public: val name: String
          |  def this(n: String) { this.name = n } }
          |val p = new P("a")
          |println(p.name())
          |""".stripMargin)
      assert(messages.nonEmpty)
      // "(name)" is the locale-independent part of the hint in both languages.
      assert(messages.exists(_.contains("(name)")), messages.mkString("; "))
    }

    it("still suggests the closest name for an ordinary typo") {
      val messages = errors("val xs = [1, 2, 3]\nprintln(xs.siz)\n")
      assert(messages.nonEmpty)
      assert(messages.exists(_.contains("size")), messages.mkString("; "))
    }

    it("does not invent an alias the receiver does not declare") {
      // String has no `size`, so `length` must not be rewritten into one.
      val messages = errors("class Q { public: def this {} }\nval q = new Q()\nprintln(q.length)\n")
      assert(messages.nonEmpty)
      assert(!messages.exists(_.contains("size")), messages.mkString("; "))
    }
  }

  describe("foreach over a Map") {
    it("names the (k, v) form instead of the desugared iterator()") {
      val messages = errors("val m = [\"a\": 1]\nforeach k: String in m { println(k) }\n")
      assert(messages.nonEmpty)
      // The old message leaked the desugaring by naming a method the user
      // never wrote; the fix must name the form they should write instead.
      assert(!messages.exists(_.contains("iterator()")), messages.mkString("; "))
      assert(messages.exists(_.contains("(k, v)")), messages.mkString("; "))
    }

    it("still accepts the destructuring form the message recommends") {
      assert(errors("val m = [\"a\": 1]\nforeach (k, v) in m { println(k + \"=\" + v) }\n").isEmpty)
    }

    it("still iterates an ordinary collection") {
      assert(errors("val xs = [1, 2]\nforeach x: Int in xs { println(x) }\n").isEmpty)
    }
  }
}
