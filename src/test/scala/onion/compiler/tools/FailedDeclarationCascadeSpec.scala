package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * One broken declaration should produce one error (issue #333).
 *
 * `val bad = xs.noSuchMethod()` used to report the real error plus a
 * "local variable bad is not found" for every later use — misleading, because
 * `bad` *is* declared (only its type is unknown), and loud enough to bury the
 * root cause in a real file.
 *
 * Binding the name at a placeholder type was tried and rejected: it traded this
 * noise for wrong-type noise on the placeholder (`Object.map() is not found` —
 * with `did you mean: wait` from Object's own methods), which is worse for the
 * common case of using the variable as a receiver. Suppressing the follow-on
 * report keeps the root cause alone.
 */
class FailedDeclarationCascadeSpec extends AnyFunSpec {

  private def errors(source: String): Seq[String] =
    new OnionCompiler(CompilerConfig(Seq("."), null, "UTF-8", "", 20))
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "S.on")))
      .allErrors
      .map(_.message)
      .toSeq

  describe("a declaration whose initializer failed to type") {
    it("reports the root cause once, not once per later use") {
      val messages = errors(
        """val xs = [1, 2, 3]
          |val bad = xs.noSuchMethod()
          |println(bad)
          |println(bad.toString())
          |println(bad)
          |""".stripMargin)
      assert(messages.length == 1, messages.mkString("; "))
      assert(messages.head.contains("noSuchMethod"), messages.head)
    }

    it("does not claim the variable is undeclared") {
      val messages = errors(
        """val xs = [1, 2, 3]
          |val bad = xs.noSuchMethod()
          |println(bad)
          |""".stripMargin)
      // "bad" is declared; only its type is unknown.
      assert(!messages.exists(_.contains("bad")), messages.mkString("; "))
    }

    it("stays silent for an assignment to the broken name too") {
      val messages = errors(
        """val xs = [1, 2, 3]
          |var bad = xs.noSuchMethod()
          |bad = 5
          |""".stripMargin)
      assert(messages.length == 1, messages.mkString("; "))
    }

    it("still reports a genuinely undeclared variable") {
      val messages = errors("val a = 1\nprintln(notDeclared)\n")
      assert(messages.exists(_.contains("notDeclared")), messages.mkString("; "))
    }

    it("still reports each distinct undeclared name") {
      val messages = errors("println(alpha)\nprintln(beta)\n")
      assert(messages.exists(_.contains("alpha")), messages.mkString("; "))
      assert(messages.exists(_.contains("beta")), messages.mkString("; "))
    }

    it("leaves a well-typed declaration and its uses alone") {
      assert(errors("val ok = [1, 2].size\nprintln(ok)\nprintln(ok + 1)\n").isEmpty)
    }
  }
}
