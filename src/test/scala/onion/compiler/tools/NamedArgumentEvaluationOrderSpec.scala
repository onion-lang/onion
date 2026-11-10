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
}
