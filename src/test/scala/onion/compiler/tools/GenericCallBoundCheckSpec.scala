package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * A generic call checks the type arguments it infers against bounds that name
 * another type parameter of the same method (`[B, T extends B]`), after every
 * type argument is known (#2325). Before, `T extends B` was checked only
 * against `B`'s own bound, so a call that broke it compiled and then threw a
 * ClassCastException.
 */
class GenericCallBoundCheckSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }
  describe("a call whose inferred type arguments break a chained bound") {
    it("is rejected when the expected return type fixes B (issue repro)") {
      val r = shell.run(
        """def g[B, T extends B](v: T): B = (v as B)
          |def main(args: String[]): Int { val s: String = g(Integer::valueOf(1)); IO::println(s); return 0 }
          |""".stripMargin, "None", Array())
      assert(Shell.Failure(-1) == r)
    }
    it("is rejected when two arguments fix B and T to unrelated types") {
      val r = shell.run(
        """def pick[B, T extends B](b: B, t: T): B = b
          |def main(args: String[]): Int { IO::println(pick("a", Integer::valueOf(1))); return 0 }
          |""".stripMargin, "None", Array())
      assert(Shell.Failure(-1) == r)
    }
    it("is rejected for an instance method too") {
      val r = shell.run(
        """class K {
          |public:
          |  def this { }
          |  def pick[B, T extends B](b: B, t: T): B = b
          |}
          |def main(args: String[]): Int { IO::println(new K().pick("a", Integer::valueOf(1))); return 0 }
          |""".stripMargin, "None", Array())
      assert(Shell.Failure(-1) == r)
    }
  }
  describe("a call whose inferred type arguments satisfy a chained bound") {
    it("compiles and runs when T is B") {
      val r = shell.run(
        """def g[B, T extends B](v: T): B = (v as B)
          |def main(args: String[]): Int { val s: String = g("abc"); return s.length() }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(3) == r)
    }
    it("compiles and runs when T is a subtype of B") {
      val r = shell.run(
        """def pick[B, T extends B](b: B, t: T): B = t
          |def main(args: String[]): Int { val n: Number = pick(Integer::valueOf(1) as Number, Integer::valueOf(7)); return n.intValue() }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(7) == r)
    }
    it("compiles and runs when B is left to its bound") {
      val r = shell.run(
        """def g[B, T extends B](v: T): B = (v as B)
          |def main(args: String[]): Int { IO::println(g(Integer::valueOf(4))); return 0 }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(0) == r)
    }
    it("leaves an F-bounded parameter alone") {
      val r = shell.run(
        """def h[T extends Comparable[T]](v: T): T = v
          |def main(args: String[]): Int { return h("xy").length() + h(3) }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(5) == r)
    }
  }
  describe("a call where nothing fixes the bounded parameter (panel blocks on #2434)") {
    it("compiles when T appears in no parameter") {
      val r = shell.run(
        """def mk[B, T extends B](b: B): B = b
          |def main(args: String[]): Int { val s: String = mk("x"); return s.length() }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(1) == r)
    }
    it("compiles when T is fixed only by an empty list literal") {
      val r = shell.run(
        """def firstOr[B, T extends B](xs: List[T], d: B): B = if xs.size > 0 { xs[0] } else { d }
          |def main(args: String[]): Int { val s: String = firstOr([], "d2"); return s.length() }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(2) == r)
    }
    it("compiles when T is fixed only by a null argument") {
      val r = shell.run(
        """def pick[B, T extends B](b: B, t: T?): B = b
          |def main(args: String[]): Int { val s: String = pick("a", null); return s.length() }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(1) == r)
    }
    it("compiles for a null argument under a nullable chained bound") {
      val r = shell.run(
        """def g[B, T extends B](v: T?): B? = null
          |def wid[B, T extends B?](v: T): B? = null
          |def main(args: String[]): Int { val s: String? = g(null); val u: Object? = wid(null); return if s == null && u == null { 4 } else { 0 } }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(4) == r)
    }
  }
  describe("a bound that is a generic type naming another parameter (#2538)") {
    it("accepts List[String] for T extends List[B] with B = String") {
      val r = shell.run(
        """import { java.util.* }
          |def w[B, T extends List[B]](b: B, t: T): B = b
          |def main(args: String[]): Int { val l: List[String] = ["a"]; return w("q", l).length() }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(1) == r)
    }
    it("still rejects List[Integer] when B is String") {
      val r = shell.run(
        """import { java.util.* }
          |def w[B, T extends List[B]](b: B, t: T): B = b
          |def main(args: String[]): Int { val l: List[Integer] = [1]; IO::println(w("q", l)); return 0 }
          |""".stripMargin, "None", Array())
      assert(Shell.Failure(-1) == r)
    }
    // A generic call that is the argument of another call was typed with its diagnostics
    // muted, and the bound error was lost; the program then failed at run time. The rule:
    // wherever such a call's term is kept, its bound error is reported, once.
    for ((label, call) <- Seq(
           "an Object parameter" -> "show(w(\"q\", l))",
           "the top-level println" -> "println(w(\"q\", l))",
           "a nested call" -> "println(use(w(\"q\", l)))",
           "an instance method" -> "new Holder(\"x\").take(w(\"q\", l))",
           "a static method" -> "Holder::st(w(\"q\", l))",
           "a constructor" -> "new Holder(w(\"q\", l))",
           "an Object constructor" -> "new Wide(w(\"q\", l))",
           "a record constructor" -> "new R(w(\"q\", l))",
           "a Java constructor" -> "new ArrayList[Object](w3(\"q\", l))",
           "a constructor inside a lambda" -> "val f: () -> Object = () -> new Holder(w(\"q\", l)); f()",
           // A try's resource initializers are children of the kept term too (panel on f9909f6).
           "a try resource inside a nested call" -> "val o = use(use(try (val r = res(w(\"q\", l))) { \"x\" }))",
           "the second of two try resources" -> "val o = use(use(try (val r = res(\"a\"); val r2 = res(w(\"q\", l))) { \"x\" }))",
           "a try resource under println" -> "println(use(try (val r = res(w(\"q\", l))) { \"x\" }))")) {
      it(s"still rejects List[Integer] when B is String, as the argument of $label") {
        val src =
          s"""import { java.util.* }
             |class Holder {
             |public:
             |  def this(s: String) { IO::println(s.length()) }
             |  def take(o: Object): void { IO::println(o) }
             |  static def st(o: Object): void { IO::println(o) }
             |}
             |class Wide {
             |public:
             |  def this(o: Object) { IO::println(o) }
             |}
             |record R(s: String)
             |def w[B, T extends List[B]](b: B, t: T): B = t[0]
             |def w3[B, T extends List[B]](b: B, t: T): T = t
             |def use(o: Object): Object = o
             |def show(o: Object): void { IO::println(o) }
             |def res(s: String): java.io.StringReader = new java.io.StringReader(s)
             |def main(args: String[]): Int { val l: List[Integer] = [1]; $call; return 0 }
             |""".stripMargin
        assert(errorCodes(src) == Seq("E0000"))
      }
    }
    for ((label, call) <- Seq("a String parameter" -> "showS(w2(\"q\", 3))", "a constructor" -> "new H(w2(\"q\", 3))")) {
      it(s"still rejects T extends B with B = String and T = Int, as the argument of $label (#2325)") {
        val src =
          s"""class H {
             |public:
             |  def this(s: String) { IO::println(s.length()) }
             |}
             |def w2[B, T extends B](b: B, t: T): B = b
             |def showS(o: String): void { IO::println(o) }
             |def main(args: String[]): Int { $call; return 0 }
             |""".stripMargin
        assert(errorCodes(src) == Seq("E0000"))
      }
    }
  }
}
