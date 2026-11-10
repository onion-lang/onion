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
}
