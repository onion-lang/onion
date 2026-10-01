package onion.compiler

import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * E0005's "did you mean" used to rank by name alone: `Format::grouped(1234567L)` was
 * answered with `group`, a private `group(String, Boolean)` helper that no program can
 * call and that could not take a `Long` anyway. Candidates are now limited to members the
 * program can call with that many arguments, and ones whose parameters also take the
 * argument types are tried first.
 *
 * The suggested name is spliced into the localized text verbatim, so substring checks on
 * identifiers hold in both locales.
 */
class CallSuggestionCompatibilitySpec extends AnyFunSpec {

  private def e0005(src: String): String = {
    val errs = new OnionCompiler(new CompilerConfig(List("."), null, "UTF-8", "", 10))
      .compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs
      case other                            => fail(s"expected a compile failure, got $other")
    }
    val found = errs.filter(_.errorCode.contains("E0005"))
    assert(found.nonEmpty, errs.map(_.message).mkString("\n"))
    found.map(_.message).mkString("\n")
  }

  it("does not suggest Format's private group helper for grouped(Long) (the dogfood report)") {
    val msg = e0005("println(Format::grouped(1234567L))\n")
    assert(msg.contains("grouped"), msg)
    assert(!msg.replace("grouped", "").contains("group"), msg)
  }

  it("prefers a candidate whose parameters take the arguments over a closer-named one that cannot") {
    val msg = e0005(
      """class K {
        |public:
        |  static def sizeOff(s: String): Int { return 0 }
        |  static def sizeOfInt(n: Int): Int { return n }
        |}
        |println(K::sizeOf(1))
        |""".stripMargin)
    assert(msg.contains("sizeOfInt"), msg)
    assert(!msg.contains("sizeOff"), msg)
  }

  it("drops a candidate no overload of which takes that many arguments") {
    val msg = e0005(
      """class K {
        |public:
        |  static def total(a: Int, b: Int): Int { return a + b }
        |}
        |println(K::totl(1))
        |""".stripMargin)
    assert(!msg.contains("total"), msg)
  }

  it("still suggests a plain typo whose arguments fit") {
    val msg = e0005(
      """def helper(x: Int): Int { return x + 1 }
        |println(helpr(1))
        |""".stripMargin)
    assert(msg.contains("helper"), msg)
  }

  it("falls back to a count-only match when no candidate takes the argument types") {
    val msg = e0005(
      """class K {
        |public:
        |  static def parse(s: String): Int { return 0 }
        |}
        |println(K::prse(1))
        |""".stripMargin)
    assert(msg.contains("parse"), msg)
  }

  it("counts default and vararg parameters when matching the argument count") {
    val withDefault = e0005(
      """class K {
        |public:
        |  static def greet(name: String, loud: Boolean = false): String { return name }
        |}
        |println(K::gret("x"))
        |""".stripMargin)
    assert(withDefault.contains("greet"), withDefault)
    // String::format(String, Object...) takes one argument or more.
    val vararg = e0005("println(String::formatt(\"%d\", 1, 2))\n")
    assert(vararg.contains("format"), vararg)
  }
}
