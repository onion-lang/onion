package onion.compiler.tools

import onion.tools.Shell

/** Named arguments run in the order they are written, then bind by name (#2767). */
class NamedArgumentEvaluationOrderSpec extends AbstractShellSpec {
  private def run(body: String): Shell.Result =
    shell.run(
      s"""
         |class Log {
         |public:
         |  val sb: StringBuilder
         |  def this { sb = new StringBuilder() }
         |  def t(s: String): String { sb.append(s); return s }
         |  def n(i: Int): Int { sb.append(i); return i }
         |}
         |class Pair {
         |public:
         |  val text: String
         |  def this(a: String, b: String) { text = a + b }
         |}
         |class Base {
         |public:
         |  def join(a: String, b: String): String = a + b
         |}
         |class Test extends Base {
         |public:
         |  static def f(a: String, b: String): String = a + b
         |  static def g(a: String, b: String = "-", c: String = "+", d: String): String = a + b + c + d
         |  static def sub(a: Int, b: Int): Int = a - b
         |  static def count(n: Int, acc: Int): Int { if n == 0 { return acc } return count(acc = acc + n, n = n - 1) }
         |  static def pick[T](a: T, b: T): T = a
         |  static def apply(f: (String) -> String, s: String): String = f(s)
         |  def inst(a: String, b: String): String = a + b
         |  def viaSuper(log: Log): String = super.join(b = log.t("B"), a = log.t("A"))
         |  static def main(args: String[]): String {
         |$body
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  describe("named arguments evaluation order") {
    it("evaluates a static call's arguments in written order") {
      assert(run(
        """    val log = new Log()
          |    val r = f(b = log.t("B"), a = log.t("A"))
          |    return log.sb.toString() + ":" + r""".stripMargin) == Shell.Success("BA:AB"))
    }

    it("evaluates an instance call's arguments in written order") {
      assert(run(
        """    val log = new Log()
          |    val r = new Test().inst(b = log.t("B"), a = log.t("A"))
          |    return log.sb.toString() + ":" + r""".stripMargin) == Shell.Success("BA:AB"))
    }

    it("evaluates a safe call's arguments in written order") {
      assert(run(
        """    val log = new Log()
          |    val t: Test? = new Test()
          |    val r = t?.inst(b = log.t("B"), a = log.t("A"))
          |    return log.sb.toString() + ":" + r""".stripMargin) == Shell.Success("BA:AB"))
    }

    it("evaluates a super call's arguments in written order") {
      assert(run(
        """    val log = new Log()
          |    val r = new Test().viaSuper(log)
          |    return log.sb.toString() + ":" + r""".stripMargin) == Shell.Success("BA:AB"))
    }

    it("evaluates a constructor's arguments in written order") {
      assert(run(
        """    val log = new Log()
          |    val p = new Pair(b = log.t("B"), a = log.t("A"))
          |    return log.sb.toString() + ":" + p.text""".stripMargin) == Shell.Success("BA:AB"))
    }

    it("keeps positional arguments first and fills defaults after the written ones") {
      assert(run(
        """    val log = new Log()
          |    val r = g(log.t("A"), d = log.t("D"), c = log.t("C"))
          |    return log.sb.toString() + ":" + r""".stripMargin) == Shell.Success("ADC:A-CD"))
    }

    it("evaluates primitive arguments in written order and passes them by name") {
      assert(run(
        """    val log = new Log()
          |    val r = sub(b = log.n(1), a = log.n(5))
          |    return log.sb.toString() + ":" + r""".stripMargin) == Shell.Success("15:4"))
    }

    it("sees a side effect of an earlier-written argument in a later one") {
      assert(run(
        """    var i = 0
          |    val r = sub(b = i++, a = i)
          |    return "" + r""".stripMargin) == Shell.Success("1"))
    }

    it("leaves in-order named arguments as they were") {
      assert(run(
        """    val log = new Log()
          |    val r = f(a = log.t("A"), b = log.t("B"))
          |    return log.sb.toString() + ":" + r""".stripMargin) == Shell.Success("AB:AB"))
    }

    it("evaluates in written order inside a lambda") {
      assert(run(
        """    val log = new Log()
          |    val k: () -> String = () -> f(b = log.t("B"), a = log.t("A"))
          |    val r = k()
          |    return log.sb.toString() + ":" + r""".stripMargin) == Shell.Success("BA:AB"))
    }

    it("keeps a tail-recursive call with swapped named arguments correct") {
      assert(run("""    return "" + count(n = 100000, acc = 0)""") == Shell.Success("705082704"))
    }

    it("evaluates a generic call's arguments in written order") {
      assert(run(
        """    val log = new Log()
          |    val r: String = pick(b = log.t("B"), a = log.t("A"))
          |    return log.sb.toString() + ":" + r""".stripMargin) == Shell.Success("BA:A"))
    }

    it("passes a lambda written after a later parameter") {
      assert(run(
        """    val log = new Log()
          |    val r = apply(s = log.t("S"), f = (x: String) -> x + "!")
          |    return log.sb.toString() + ":" + r""".stripMargin) == Shell.Success("S:S!"))
    }
  }

  // A field initializer is emitted into every constructor. Its temporaries (and its own
  // block locals) used to land in the constructor's parameter slots (#2800 panel).
  describe("an @TailRecursive group") {
    it("still becomes a loop when a tail call names side-effect-free arguments out of order") {
      val result = shell.run(
        """class M {
          |private:
          |  static @TailRecursive def isOdd(n: Int, steps: Int): Boolean = if n == 0 { false } else { isEven(n - 1, steps + 1) }
          |  static @TailRecursive def isEven(n: Int, steps: Int): Boolean = if n == 0 { true } else { isOdd(steps = steps + 1, n = n - 1) }
          |public:
          |  static def main(args: String[]): String = "" + isEven(100000000, 0)
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(result == Shell.Success("true"))
    }

    // #2800 panel, round 3: a reordered argument that calls something needs a temporary.
    // The state machine evaluates a tail call's arguments into its own slots, in written
    // order, so the group stays a loop instead of falling back with W0016.
    it("still becomes a loop when a reordered tail-call argument calls a method") {
      val result = shell.run(
        """class M {
          |private:
          |  static def id(i: Int): Int = i
          |  static @TailRecursive def ev(n: Int, s: Int): Boolean = if n == 0 { true } else { od(n - 1, s) }
          |  static @TailRecursive def od(n: Int, s: Int): Boolean = if n == 0 { false } else { ev(s = id(s), n = n - 1) }
          |public:
          |  static def main(args: String[]): String = "" + ev(100000000, 0)
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(result == Shell.Success("true"))
    }

    it("evaluates a reordered tail call's arguments in written order inside the loop") {
      val result = shell.run(
        """class M {
          |private:
          |  static val sb: StringBuilder = new StringBuilder()
          |  static def t(i: Int): Int { sb.append(i); return i }
          |  static @TailRecursive def a(n: Int, s: Int): String = if n == 0 { sb.toString() } else { b(n - 1, s) }
          |  static @TailRecursive def b(n: Int, s: Int): String = if n == 0 { sb.toString() } else { a(s = t(s + 1), n = t(n - 1)) }
          |public:
          |  static def main(args: String[]): String = a(4, 0) + ":" + a(200001, 0).length()
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(result.isInstanceOf[Shell.Success])
      assert(result.asInstanceOf[Shell.Success].value.toString.startsWith("1220:"))
    }

    // #2800 panel, round 4: a reordered call anywhere in a member, not only the tail call,
    // keeps the group a loop. `main` optimized all three before named arguments ran in
    // written order.
    def runGroup(members: String, entry: String): Shell.Result =
      shell.run(
        s"""class M {
           |private:
           |  static val sb: StringBuilder = new StringBuilder()
           |  static def id(x: Int): Int { return x }
           |  static def t(x: Int): Int { sb.append(x); return x }
           |  static def mix(a: Int, b: Int): Int { return a + b - a }
           |  static def cat(a: String, b: String): String { return a + b }
           |  static def lmix(a: Long, b: Long): Long { return a * 10L + b }
           |$members
           |public:
           |  static def main(args: String[]): String = $entry
           |}
           |""".stripMargin,
        "None",
        Array()
      )

    it("stays a loop with a reordered call in a member's condition") {
      assert(runGroup(
        """  static @TailRecursive def ev(n: Int, s: Int): Int = if n == 0 { s } else { od(n - 1, s) }
          |  static @TailRecursive def od(n: Int, s: Int): Int {
          |    if n == 0 { return s }
          |    if mix(b = id(s), a = id(n)) < 0 { return -1 }
          |    return ev(n - 1, s + 1)
          |  }""".stripMargin,
        """"" + ev(4, 0) + ":" + ev(10000000, 0)""") == Shell.Success("2:5000000"))
    }

    it("stays a loop with a reordered call inside a tail-call argument") {
      assert(runGroup(
        """  static @TailRecursive def ev(n: Int, s: Int): Int = if n == 0 { s } else { od(n - 1, s) }
          |  static @TailRecursive def od(n: Int, s: Int): Int = if n == 0 { s } else { ev(n - 1, mix(b = id(s + 1), a = id(n))) }""".stripMargin,
        """"" + ev(4, 0) + ":" + ev(10000000, 0)""") == Shell.Success("2:5000000"))
    }

    it("stays a loop with a reordered call inside a reordered tail call") {
      assert(runGroup(
        """  static @TailRecursive def ev(n: Int, s: Int): Int = if n == 0 { s } else { od(n - 1, s) }
          |  static @TailRecursive def od(n: Int, s: Int): Int = if n == 0 { s } else { ev(s = mix(b = id(s + 1), a = id(s)), n = n - 1) }""".stripMargin,
        """"" + ev(4, 0) + ":" + ev(10000000, 0)""") == Shell.Success("2:5000000"))
    }

    it("keeps reordered calls of different types in different members apart, in written order") {
      assert(runGroup(
        """  static @TailRecursive def a(n: Int, s: Int): String {
          |    if n == 0 { return sb.toString() }
          |    if cat(b = "" + t(1), a = "" + t(2)).length() < 0 { return "" }
          |    return b(n - 1, s)
          |  }
          |  static @TailRecursive def b(n: Int, s: Int): String {
          |    if n == 0 { return sb.toString() }
          |    if lmix(b = 0L + t(3), a = 0L + t(4)) < 0L { return "" }
          |    return a(n - 1, s)
          |  }""".stripMargin,
        """a(2, 0) + ":" + a(1000000, 0).length()""") == Shell.Success("1234:2000004"))
    }

    it("still evaluates arguments with side effects in written order") {
      assert(run(
        """    val log = new Log()
          |    val r = sub(b = log.n(1) + 1, a = log.n(9))
          |    return log.sb.toString() + ":" + r""".stripMargin) == Shell.Success("19:7"))
    }

    it("leaves side-effect-free arguments where they are") {
      assert(run(
        """    var x: Int = 10
          |    val y: Int = 3
          |    val r = sub(b = y * 2, a = -x + 20)
          |    return "" + r""".stripMargin) == Shell.Success("4"))
    }
  }

  describe("a field initializer's locals") {
    def runClass(classes: String, body: String): Shell.Result =
      shell.run(
        s"""
           |class Log {
           |public:
           |  static val sb: StringBuilder = new StringBuilder()
           |  static def t(s: String): String { sb.append(s); return s }
           |  static def f(a: String, b: String): String = a + b
           |}
           |$classes
           |class Test {
           |public:
           |  static def main(args: String[]): String {
           |$body
           |  }
           |}
           |""".stripMargin,
        "None",
        Array()
      )

    it("does not overwrite a String constructor parameter") {
      assert(runClass(
        """class Box {
          |public:
          |  val s: String = Log::f(b = Log::t("B"), a = Log::t("A"))
          |  val n: String
          |  def this(n: String) { this.n = n }
          |}""".stripMargin,
        """    val b = new Box("hello")
          |    return Log::sb.toString() + ":" + b.s + ":" + b.n""".stripMargin) == Shell.Success("BA:AB:hello"))
    }

    it("does not clash with a Long or Int primary constructor parameter") {
      assert(runClass(
        """class LBox(val n: Long) {
          |public:
          |  val s: String = Log::f(b = Log::t("B"), a = Log::t("A"))
          |}
          |class IBox(val n: Int) {
          |public:
          |  val s: String = Log::f(b = Log::t("D"), a = Log::t("C"))
          |}""".stripMargin,
        """    val l = new LBox(7L)
          |    val i = new IBox(3)
          |    return Log::sb.toString() + ":" + l.s + l.n + ":" + i.s + i.n""".stripMargin) == Shell.Success("BADC:AB7:CD3"))
    }

    it("keeps a block local in an initializer apart from the constructor's parameters") {
      assert(runClass(
        """class Box {
          |public:
          |  val s: String = if true { val t: String = "local"; t } else { "no" }
          |  val n: String
          |  def this(n: String) { this.n = n }
          |}
          |class LBox(val n: Long) {
          |public:
          |  val s: String = if true { val t: String = "local"; t } else { "no" }
          |}""".stripMargin,
        """    val b = new Box("hello")
          |    val l = new LBox(7L)
          |    return b.s + ":" + b.n + ":" + l.s + ":" + l.n""".stripMargin) == Shell.Success("local:hello:local:7"))
    }

    it("keeps a local captured and mutated by a closure in an initializer apart") {
      assert(runClass(
        """class Ctr {
          |public:
          |  val inc: () -> Int = if true { var c: Int = 0; val f: () -> Int = () -> { c += 1; c }; f } else { () -> 0 }
          |  val tag: String
          |  def this(tag: String) { this.tag = tag }
          |}""".stripMargin,
        """    val k = new Ctr("t")
          |    val g = k.inc
          |    g()
          |    return "" + g() + ":" + k.tag""".stripMargin) == Shell.Success("2:t"))
    }

    it("keeps two initializers' locals of different types apart") {
      assert(runClass(
        """class Two(val k: Int) {
          |public:
          |  val a: Long = if true { val x: Long = 40L; x + 2L } else { 0L }
          |  val b: String = if true { val y: String = "y"; y + k } else { "" }
          |  static val c: Long = if true { val x: Long = 1L; x } else { 0L }
          |  static val d: String = if true { val y: String = "s"; y } else { "" }
          |}""".stripMargin,
        """    val t = new Two(5)
          |    return "" + t.a + ":" + t.b + ":" + Two::c + ":" + Two::d""".stripMargin) == Shell.Success("42:y5:1:s"))
    }
  }

  describe("line numbers of a reordered call") {
    // A reordered call can start at the method's first instruction. The method's own
    // `def` line was recorded at that instruction too, and the JVM reports the first
    // entry it finds there, so a failure in the call said "called from" the `def` line.
    def callerLine(callLine: String): Shell.Result =
      shell.run(
        s"""class Probe {
           |public:
           |  static def boom(): Int { throw new RuntimeException("boom") }
           |  static def f(a: String, b: Int): String = a + b
           |  static def call(): String {
           |$callLine
           |  }
           |  static def main(args: String[]): String {
           |    try { call() } catch e: RuntimeException { return "line " + e.getStackTrace()[1].getLineNumber() }
           |    return "no exception"
           |  }
           |}
           |""".stripMargin,
        "None",
        Array()
      )

    it("reports the call's line, not the def line, for a reordered first statement") {
      assert(callerLine("""    return f(b = boom(), a = "A")""") == Shell.Success("line 6"))
    }

    it("reports the call's line for a call written in order") {
      assert(callerLine("""    return f("A", boom())""") == Shell.Success("line 6"))
    }

    it("reports the call's line when the throwing call is the method's first instruction") {
      assert(callerLine("""    IO::println(boom())
          |    return "unreached"""".stripMargin) == Shell.Success("line 6"))
    }
  }
}
