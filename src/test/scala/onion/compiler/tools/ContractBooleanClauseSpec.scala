package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilationOutcome, CompileError, CompilerConfig, ContractMode, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

/**
 * A contract clause that is not a Boolean condition (#2605, item 8). It stays E0000, but the
 * message says the clause must be a condition and shows one, instead of "consider a cast"
 * (no cast turns an `Int` into a condition) or the type-parameter hint meant for casts.
 *
 * Assertions use error codes and the clause written in backticks, which are the same in en
 * and ja; never localized prose.
 */
class ContractBooleanClauseSpec extends AnyFunSpec {

  private def compile(source: String): Seq[CompileError] = {
    val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 10, runContracts = false, contracts = ContractMode.Check)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(source), "B.on"))) match {
      case CompilationOutcome.Failure(errs) => errs
      case _                                => Nil
    }
  }

  private def single(source: String): CompileError = {
    val errs = compile(source)
    assert(errs.size == 1, errs.map(_.message).mkString("\n"))
    errs.head
  }

  private def method(clause: String, params: String = "x: Int"): String =
    s"""class K {
       |public:
       |  static def f($params): Int $clause { return 1 }
       |}
       |println(1)""".stripMargin

  describe("a non-Boolean contract clause") {
    it("an Int `requires` says the clause is a condition, with an example, and no cast advice") {
      val e = single(method("requires x"))
      assert(e.errorCode.contains("E0000"))
      assert(e.message.contains("`requires n > 0`"), e.message)
      assert(!e.message.contains("cast"), e.message)
      assert(!e.message.contains("キャスト"), e.message)
    }

    it("an `ensures result` on an Int method names `ensures`") {
      val e = single(method("ensures result"))
      assert(e.errorCode.contains("E0000"))
      assert(e.message.contains("`ensures n > 0`"), e.message)
    }

    it("a String `requires` gets the same hint") {
      val e = single(method("requires s", "s: String"))
      assert(e.errorCode.contains("E0000"))
      assert(e.message.contains("`requires s != null`"), e.message)
    }

    it("a type-parameter `requires` gets the clause hint, not the cast hint") {
      val src =
        """class K {
          |public:
          |  static def f[T](t: T): Int requires t { return 1 }
          |}
          |println(1)""".stripMargin
      val e = single(src)
      assert(e.errorCode.contains("E0000"))
      assert(e.message.contains("`requires n > 0`"), e.message)
      assert(!e.message.contains("Generic.Case"), e.message)
    }

    it("a literal `requires null` gets the clause hint, not cast advice") {
      val e = single(method("requires null"))
      assert(e.errorCode.contains("E0000"))
      assert(e.message.contains("`requires n > 0`"), e.message)
      assert(!e.message.contains("cast"), e.message)
      assert(!e.message.contains("キャスト"), e.message)
    }

    it("an `ensures null` names `ensures`") {
      val e = single(method("ensures null"))
      assert(e.errorCode.contains("E0000"))
      assert(e.message.contains("`ensures n > 0`"), e.message)
    }

    it("an `invariant` names `invariant`") {
      val src =
        """class C {
          |  var n: Int
          |  invariant n
          |public:
          |  def this { n = 0 }
          |}
          |println(new C())""".stripMargin
      val e = single(src)
      assert(e.errorCode.contains("E0000"))
      assert(e.message.contains("`invariant n > 0`"), e.message)
    }
  }

  describe("a Boolean clause is unchanged") {
    it("Boolean and a boxed java.lang.Boolean compile") {
      val src =
        """import { java.lang.Boolean as JB }
          |class K {
          |public:
          |  static def f(b: Boolean): Int requires b { return 1 }
          |  static def g(j: JB): Int requires j { return 1 }
          |}
          |println(K::f(true))""".stripMargin
      assert(compile(src).isEmpty, compile(src).map(_.message).mkString("\n"))
    }

    it("a nullable Boolean is still the null-safety error E0070") {
      val e = single(method("requires j", "j: Boolean?"))
      assert(e.errorCode.contains("E0070"))
    }
  }
}
