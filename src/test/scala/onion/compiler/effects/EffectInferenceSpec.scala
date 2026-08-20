package onion.compiler.effects

import java.io.StringReader
import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

/**
 * Transitive effect inference (issue #356): per-method effect sets computed from the
 * typed AST, joined through user calls, with `unknown` for anything unvouched.
 */
class EffectInferenceSpec extends AnyFunSpec {
  import Effect._

  private def infer(source: String): Map[String, Set[Effect]] = {
    val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 10, checkLaws = false)
    val result = new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "Effects.on")))
    assert(!result.hasErrors, s"compile failed: ${result.allErrors.map(_.message).mkString("; ")}")
    val typed = result.debugArtifacts.typedClasses.getOrElse(Seq.empty)
    EffectInference.infer(typed)
      .map(me => s"${me.className}#${me.methodName}" -> me.effects).toMap
  }

  describe("direct calls") {
    it("takes the table's verdict for a stdlib call") {
      val eff = infer(
        """class A {
          |public:
          |  static def f(p: String): String = Files::readText(p)
          |}""".stripMargin)
      assert(eff("A#f") == Set(Read))
    }

    it("reports an unlisted Java call as unknown") {
      val eff = infer(
        """import { java.io.BufferedReader; java.io.FileReader; }
          |class A {
          |public:
          |  static def f(p: String): String {
          |    val r = new BufferedReader(new FileReader(p))
          |    return r.readLine()
          |  }
          |}""".stripMargin)
      assert(eff("A#f").contains(Unknown))
    }
  }

  describe("transitivity") {
    it("joins effects across user calls") {
      val eff = infer(
        """class A {
          |public:
          |  static def leaf(p: String): String = Files::readText(p)
          |  static def mid(p: String): String = leaf(p)
          |  static def top(p: String): void { IO::println(mid(p)) }
          |}""".stripMargin)
      assert(eff("A#leaf") == Set(Read))
      assert(eff("A#mid") == Set(Read))
      assert(eff("A#top") == Set(Read, Console))
    }

    it("converges on direct and mutual recursion") {
      val eff = infer(
        """class A {
          |public:
          |  static def even(n: Int): Boolean { if n == 0 { return true } else { return odd(n - 1) } }
          |  static def odd(n: Int): Boolean { if n == 0 { return false } else { return even(n - 1) } }
          |  static def down(n: Int): Int { if n <= 0 { return 0 } else { return down(n - 1) } }
          |  static def noisy(n: Int): Int {
          |    IO::println("" + n)
          |    if n <= 0 { return 0 } else { return noisy(n - 1) }
          |  }
          |}""".stripMargin)
      assert(eff("A#even").isEmpty)
      assert(eff("A#odd").isEmpty)
      assert(eff("A#down").isEmpty)
      assert(eff("A#noisy") == Set(Console))
    }
  }

  describe("closures") {
    it("charges the closure body to the method that creates it") {
      val eff = infer(
        """class A {
          |public:
          |  static def maker(): Int {
          |    val f = () -> { IO::println("hi") }
          |    f()
          |    return 0
          |  }
          |}""".stripMargin)
      assert(eff("A#maker") == Set(Console))
    }
  }

  describe("constructors") {
    it("infers a constructor body's effects and flows them into `new`") {
      val eff = infer(
        """class Loud {
          |public:
          |  def this { IO::println("built") }
          |}
          |class A {
          |public:
          |  static def build(): Loud = new Loud()
          |}""".stripMargin)
      assert(eff("Loud#<init>") == Set(Console))
      assert(eff("A#build") == Set(Console))
    }

    it("charges a user superclass constructor to the subclass constructor") {
      val eff = infer(
        """class Base {
          |public:
          |  def this { IO::println("base") }
          |}
          |class Sub extends Base {
          |public:
          |  def this { }
          |}""".stripMargin)
      assert(eff("Sub#<init>") == Set(Console))
    }

    it("charges the primary's effects to a secondary that delegates to it") {
      // Self-delegation resolves against the class's own constructors, which the
      // inference over-approximates to "any constructor of this class"; that still has
      // to converge and to carry the primary's console effect into the secondary. This
      // path had no test while `: this(...)` was rare; the delegation rule makes it the
      // ordinary way to write a secondary.
      val eff = infer(
        """class Loud(val n: Int) {
          |public:
          |  var seen: Int = { IO::println("built"); n }
          |  def this : this(1) { }
          |}
          |class A {
          |public:
          |  static def build(): Loud = new Loud()
          |}""".stripMargin)
      assert(eff("A#build") == Set(Console))
    }
  }

  describe("the Method-trait default") {
    it("answers unknown for an unlisted method and the table's verdict for a listed one") {
      assert(EffectTable.effectsOrUnknown("no.such.Class", "m") == Set(Unknown))
      assert(EffectTable.effectsOrUnknown("onion.Files", "readText") == Set(Read))
    }
  }
}
