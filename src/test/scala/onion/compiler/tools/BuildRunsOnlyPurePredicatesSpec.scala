package onion.compiler.tools

import java.io.{ByteArrayOutputStream, PrintStream, StringReader}
import onion.compiler.{CompilerConfig, ContractMode, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

/**
 * The build runs a contract predicate only when that cannot reach the outside world (#2601,
 * design `docs/design/contracts.md` §6): a closed predicate, or the code an `E0112` replay runs,
 * whose effects go beyond `mutate` is not run, and the warning W0023 says so. A run that does
 * not finish in time is stopped waiting for, with the same warning, so it cannot hang the build.
 */
class BuildRunsOnlyPurePredicatesSpec extends AnyFunSpec {
  private final case class Outcome(errors: Seq[String], w23: Seq[String], stdout: String)

  private def compile(src: String, timeoutMillis: Long = 10000L): Outcome = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On,
      contracts = ContractMode.Check, contractTimeoutMillis = timeoutMillis)
    val out = new ByteArrayOutputStream()
    val saved = System.out
    val r =
      try {
        System.setOut(new PrintStream(out, true, "UTF-8"))
        new OnionCompiler(config).compileDetailed(Seq(new StreamInputSource(() => new StringReader(src), "P.on")))
      } finally System.setOut(saved)
    Outcome(r.diagnostics.errors.flatMap(_.errorCode),
      r.diagnostics.warnings.filter(_.category.code == "W0023").map(_.message), out.toString("UTF-8"))
  }

  describe("a closed predicate with an outside effect") {
    it("is not run, and W0023 names it and the effect") {
      val o = compile("""static def isReady(): Boolean ensures result = { IO::println("BUILD-TIME SIDE EFFECT"); true }""")
      assert(!o.stdout.contains("BUILD-TIME SIDE EFFECT"), o.toString)
      assert(o.errors.isEmpty, o.toString)
      assert(o.w23.size == 1 && o.w23.head.contains("isReady") && o.w23.head.contains("console"), o.toString)
    }

    it("is not run when the effect comes through a method it calls") {
      val o = compile(
        """def shout(): void { IO::println("CALLEE RAN") }
          |def ok(): Boolean ensures result { shout(); return false }""".stripMargin)
      assert(!o.stdout.contains("CALLEE RAN"), o.toString)
      assert(o.errors.isEmpty, o.toString)
      assert(o.w23.exists(_.contains("ok")), o.toString)
    }

    it("is not run when it calls something the effect table cannot vouch for") {
      val o = compile("""def bye(): Boolean ensures result { java.lang.System::exit(0); return true }""")
      assert(o.errors.isEmpty, o.toString)
      assert(o.w23.exists(w => w.contains("bye")), o.toString)
    }
  }

  describe("a closed predicate whose effect hides behind dispatch or a static initializer") {
    it("is not run when it calls an interface method a program class implements with an effect") {
      val o = compile(
        """interface Sayer { def say(): Boolean }
          |class Loud conforms Sayer {
          |public:
          |  def this {}
          |  def say(): Boolean { IO::println("IFACE RAN"); return true }
          |}
          |def mk(): Sayer = new Loud()
          |def p(): Boolean ensures result { val s: Sayer = mk(); return s.say() }""".stripMargin)
      assert(!o.stdout.contains("IFACE RAN"), o.toString)
      assert(o.errors.isEmpty, o.toString)
      assert(o.w23.exists(w => w.contains("`p`") && w.contains("console")), o.toString)
    }

    it("is not run when it calls a method a subclass overrides with an effect") {
      val o = compile(
        """class Base {
          |public:
          |  def this {}
          |  def say(): Boolean = true
          |}
          |class Loud extends Base {
          |public:
          |  def this {}
          |  override def say(): Boolean { IO::println("OVERRIDE RAN"); return true }
          |}
          |def mk(): Base = new Loud()
          |def p(): Boolean ensures result { val s: Base = mk(); return s.say() }""".stripMargin)
      assert(!o.stdout.contains("OVERRIDE RAN"), o.toString)
      assert(o.errors.isEmpty, o.toString)
      assert(o.w23.exists(w => w.contains("`p`") && w.contains("console")), o.toString)
    }

    it("is not run when its own class's static initializer has an effect") {
      val o = compile(
        """class H {
          |  static val X: Int = H::noisy()
          |public:
          |  static def noisy(): Int { IO::println("STATIC INIT RAN"); return 1 }
          |  static def p(): Boolean ensures result { return true }
          |}""".stripMargin)
      assert(!o.stdout.contains("STATIC INIT RAN"), o.toString)
      assert(o.errors.isEmpty, o.toString)
      assert(o.w23.exists(w => w.contains("`p`") && w.contains("console")), o.toString)
    }

    it("is not run when it uses another class whose static initializer has an effect") {
      val o = compile(
        """class Other {
          |  static val X: Int = Other::noisy()
          |public:
          |  static def noisy(): Int { IO::println("OTHER INIT RAN"); return 1 }
          |  static def one(): Int = 1
          |}
          |def p(): Boolean ensures result { return Other::one() == 1 }""".stripMargin)
      assert(!o.stdout.contains("OTHER INIT RAN"), o.toString)
      assert(o.errors.isEmpty, o.toString)
      assert(o.w23.exists(w => w.contains("`p`") && w.contains("console")), o.toString)
    }

    it("still runs through an interface whose implementations only touch memory: a false one is E0113") {
      val o = compile(
        """interface Sayer { def say(): Boolean }
          |class Quiet conforms Sayer {
          |public:
          |  def this {}
          |  def say(): Boolean = true
          |}
          |def mk(): Sayer = new Quiet()
          |def q(): Boolean ensures result { val s: Sayer = mk(); return !s.say() }""".stripMargin)
      assert(o.errors.contains("E0113"), o.toString)
      assert(o.w23.isEmpty, o.toString)
    }

    it("still runs when it reads an enum constant (an enum's own initializer is not an effect)") {
      val o = compile(
        """enum Color { RED, GREEN }
          |def red(): Boolean ensures result = Color::RED == Color::GREEN""".stripMargin)
      assert(o.errors.contains("E0113"), o.toString)
      assert(o.w23.isEmpty, o.toString)
    }
  }

  describe("a closed predicate that only touches memory") {
    it("still runs: a false one is E0113") {
      val o = compile(
        """def sizes(): Boolean ensures result {
          |  val xs = new java.util.ArrayList[Int]()
          |  xs.add(1)
          |  return xs.size == 2
          |}""".stripMargin)
      assert(o.errors.contains("E0113"), o.toString)
      assert(o.w23.isEmpty, o.toString)
    }

    it("still runs when it builds a generic record (the constructor of `R[String]` is the program's own)") {
      val o = compile(
        """record R[T](rank: Int, item: T)
          |def ranked(): Boolean ensures result = new R[String](1, "E").rank() == 2""".stripMargin)
      assert(o.errors.contains("E0113"), o.toString)
      assert(o.w23.isEmpty, o.toString)
    }

    it("still runs: a true one is silent") {
      val o = compile("def two(): Boolean ensures result = 1 + 1 == 2")
      assert(o.errors.isEmpty && o.w23.isEmpty, o.toString)
    }
  }

  describe("a closed predicate that does not finish") {
    it("stops the build waiting, with W0023 saying it did not finish") {
      val start = System.nanoTime()
      val o = compile(
        """def spin(): Boolean ensures result {
          |  var i = 0
          |  while true { i = i + 1 }
          |  return true
          |}""".stripMargin, timeoutMillis = 300L)
      val seconds = (System.nanoTime() - start) / 1e9
      assert(seconds < 20, s"took $seconds s")
      assert(o.errors.isEmpty, o.toString)
      assert(o.w23.size == 1 && o.w23.head.contains("spin"), o.toString)
    }
  }

  // The rule on #2601: the build runs a predicate only when everything a call to it can run
  // (every overload it may be, what library code calls back, every static initializer) stays
  // within `mutate`.
  describe("a closed predicate whose effect the library calls back, or a superclass initializer runs") {
    it("is not run when string concatenation calls a toString with an effect, and W0023 names it") {
      val o = compile(
        """class T {
          |public:
          |  def this {}
          |  def toString(): String { IO::println("TOSTRING RAN"); return "t" }
          |}
          |class C {
          |public:
          |  static def p(): Boolean ensures result { return ("" + new T()) == "t" }
          |}""".stripMargin)
      assert(!o.stdout.contains("TOSTRING RAN"), o.toString)
      assert(o.errors.isEmpty, o.toString)
      assert(o.w23.exists(w => w.contains("`p`") && w.contains("T::toString")), o.toString)
    }

    it("is not run when interpolation, a map literal key, `==` or a thrown exception reach an effect") {
      val bodies = Seq(
        "toString(): String" -> """return "#{new Q()}" == "q"""",
        "hashCode(): Int" -> """val m = [new Q(): 1]; return m.size == 1""",
        "equals(o: Object): Boolean" -> """val a: Object = new Q(); return a == new Q()""",
        "toString(): String" -> """val xs = [new Q()]; return xs.toString() == "[q]"""")
      for ((sig, body) <- bodies) {
        val ret = if (sig.endsWith("String")) "\"q\"" else if (sig.endsWith("Int")) "1" else "true"
        val o = compile(
          s"""class Q {
             |public:
             |  def this {}
             |  def $sig { IO::println("CALLBACK RAN"); return $ret }
             |}
             |class C {
             |public:
             |  static def p(): Boolean ensures result { $body }
             |}""".stripMargin)
        assert(!o.stdout.contains("CALLBACK RAN"), s"$body: $o")
        assert(o.w23.exists(_.contains("`p`")), s"$body: $o")
      }
      val thrown = compile(
        """class Ex extends RuntimeException("x") {
          |public:
          |  def getMessage(): String { IO::println("MSG RAN"); return "m" }
          |}
          |class C {
          |public:
          |  static def p(): Boolean ensures result { throw new Ex() }
          |}""".stripMargin)
      assert(!thrown.stdout.contains("MSG RAN"), thrown.toString)
      assert(thrown.w23.exists(_.contains("Ex::getMessage")), thrown.toString)
    }

    it("still runs when the toString it reaches only touches memory: a false one is E0113") {
      val o = compile(
        """class P {
          |public:
          |  def this {}
          |  def toString(): String { return "p" }
          |}
          |class C {
          |public:
          |  static def p(): Boolean ensures result { return ("" + new P()) == "q" }
          |}""".stripMargin)
      assert(o.errors.contains("E0113"), o.toString)
      assert(o.w23.isEmpty, o.toString)
    }

    it("still runs string concatenation when only an unrelated class's callback has an effect") {
      val o = compile(
        """class R conforms AutoCloseable {
          |public:
          |  def this {}
          |  def close(): void { IO::println("CLOSE RAN") }
          |}
          |class C {
          |public:
          |  static def p(): Boolean ensures result { val s: String = "a"; return (s + "b") == "abc" }
          |}""".stripMargin)
      assert(o.errors.contains("E0113"), o.toString)
      assert(o.w23.isEmpty, o.toString)
    }

    it("is not run when a superclass's static initializer has an effect, and W0023 names that class") {
      val o = compile(
        """class A {
          |  static val X: Int = A::noisy()
          |public:
          |  static def noisy(): Int { IO::println("SUPER INIT RAN"); return 1 }
          |  def this {}
          |}
          |class B extends A {
          |public:
          |  def this {}
          |  static def p(): Boolean ensures result { return true }
          |}""".stripMargin)
      assert(!o.stdout.contains("SUPER INIT RAN"), o.toString)
      assert(o.w23.exists(w => w.contains("`p`") && w.contains("`A`")), o.toString)
    }

    it("names the static initializer when the predicate's own body is pure") {
      val o = compile(
        """class Cfg {
          |  static val N: Int = Cfg::loud()
          |public:
          |  static def loud(): Int { IO::println("CFG INIT"); return 2 }
          |  static def ok(): Boolean ensures result = 1 + 1 == 2
          |}""".stripMargin)
      assert(!o.stdout.contains("CFG INIT"), o.toString)
      assert(o.w23.exists(w => w.contains("`ok`") && w.contains("`Cfg`")), o.toString)
    }

    it("still runs when a static initializer only fills its own class's fields: a false one is E0113") {
      val o = compile(
        """class K {
          |  static val N: Int = 3
          |  static val L: List[Int] = [1, 2]
          |public:
          |  static def p(): Boolean ensures result { return K::N == 4 }
          |}""".stripMargin)
      assert(o.errors.contains("E0113"), o.toString)
      assert(o.w23.isEmpty, o.toString)
    }

    it("is not run when an overload with the same name and arity has an effect, in either order") {
      val first =
        """  static def p(x: Int): Int { return x }
          |  static def p(s: String): Boolean ensures result { IO::println("OVERLOAD RAN"); return true }""".stripMargin
      val second = first.split("\n").reverse.mkString("\n")
      for (members <- Seq(first, second)) {
        val o = compile(s"class C {\npublic:\n$members\n}")
        assert(!o.stdout.contains("OVERLOAD RAN"), o.toString)
        assert(o.w23.exists(_.contains("`p`")), o.toString)
      }
    }
  }

  describe("the E0112 replay of a history counterexample") {
    it("does not run a constructor with an outside effect") {
      val o = compile(
        """class Counter {
          |  history n >= old(n)
          |public:
          |  var n: Int
          |  def this { IO::println("CTOR RAN"); n = 0 }
          |  def inc(): void { n = n + 1 }
          |}""".stripMargin)
      assert(!o.stdout.contains("CTOR RAN"), o.toString)
      assert(!o.errors.contains("E0112"), o.toString)
    }

    it("still replays a constructor that only touches memory (E0112)") {
      val o = compile(
        """class Counter {
          |  history n >= old(n)
          |public:
          |  var n: Int
          |  def this { n = 0 }
          |  def inc(): void { n = n + 1 }
          |}""".stripMargin)
      assert(o.errors.contains("E0112"), o.toString)
    }
  }
}
