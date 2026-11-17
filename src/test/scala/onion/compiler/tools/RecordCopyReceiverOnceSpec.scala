package onion.compiler.tools

import onion.tools.Shell

/**
 * `copy(..)` runs its receiver expression once, however many components it keeps
 * (#3098). It used to re-run the receiver for each omitted component, so the new
 * record mixed fields from different objects.
 */
class RecordCopyReceiverOnceSpec extends AbstractShellSpec {
  private val prelude =
    """record R(a: String, b: String, c: String)
      |class Maker {
      |  var n: Int
      |public:
      |  def this { n = 0 }
      |  def count: Int = n
      |  def mk(): R { n = n + 1; return new R("a" + n, "b" + n, "c" + n) }
      |  def mkOrNull(give: Boolean): R? { n = n + 1; if give { return new R("a" + n, "b" + n, "c" + n) } return null }
      |}
      |""".stripMargin

  private def run(body: String, name: String): Shell.Result =
    shell.run(prelude + body, name, Array())

  describe("copy(..) on a receiver expression") {
    it("runs the receiver once for a partial copy") {
      val result = run(
        """class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val m = new Maker
          |    val r = m.mk().copy(c = "C")
          |    return r.toString() + " calls=" + m.count
          |  }
          |}
          |""".stripMargin, "CopyOnceNamed.on")
      assert(Shell.Success("R(a=a1, b=b1, c=C) calls=1") == result)
    }

    it("runs the receiver once for a zero-argument clone") {
      val result = run(
        """class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val m = new Maker
          |    val r = m.mk().copy()
          |    return r.toString() + " calls=" + m.count
          |  }
          |}
          |""".stripMargin, "CopyOnceClone.on")
      assert(Shell.Success("R(a=a1, b=b1, c=c1) calls=1") == result)
    }

    it("runs the receiver once for named arguments out of order") {
      val result = run(
        """class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val m = new Maker
          |    val r = m.mk().copy(c = "C", a = "A")
          |    return r.toString() + " calls=" + m.count
          |  }
          |}
          |""".stripMargin, "CopyOnceReordered.on")
      assert(Shell.Success("R(a=A, b=b1, c=C) calls=1") == result)
    }

    it("runs a safe-call receiver once, and still gives null for a null receiver") {
      val result = run(
        """class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val m = new Maker
          |    val r = m.mkOrNull(true)?.copy(c = "C", a = "A")
          |    val z = m.mkOrNull(false)?.copy(b = "B")
          |    val w = m.mkOrNull(true)?.copy()
          |    return r + " " + z + " " + w + " calls=" + m.count
          |  }
          |}
          |""".stripMargin, "CopyOnceSafe.on")
      assert(Shell.Success("R(a=A, b=b1, c=C) null R(a=a3, b=b3, c=c3) calls=3") == result)
    }

    it("keeps reading a local receiver directly") {
      val result = run(
        """class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val m = new Maker
          |    val p = m.mk()
          |    val q = p.copy(b = "B")
          |    return q.toString() + " calls=" + m.count
          |  }
          |}
          |""".stripMargin, "CopyLocal.on")
      assert(Shell.Success("R(a=a1, b=B, c=c1) calls=1") == result)
    }

    it("keeps a @TailRecursive mutual group optimized when it copies a call result") {
      // Panel block on #3102: the receiver's temporary made the group fall back (W0016)
      // and overflow the stack.
      val result = shell.run(
        """record R(a: String, b: String, c: String)
          |class MR {
          |public:
          |  static def mk(): R = new R("a", "b", "c")
          |  static def main(args: String[]): String = "" + ev(1000000, 0)
          |private:
          |  static @TailRecursive def ev(k: Int, acc: Int): Int {
          |    if k == 0 { return acc }
          |    return od(k - 1, acc + mk().copy(a = "E").b().length())
          |  }
          |  static @TailRecursive def od(k: Int, acc: Int): Int {
          |    if k == 0 { return acc }
          |    return ev(k - 1, acc + mk().copy(a = "O").c().length())
          |  }
          |}
          |""".stripMargin, "CopyInMutualGroup.on", Array())
      assert(Shell.Success("1000000") == result)
    }

    it("keeps a @TailRecursive mutual group optimized when a tail call's argument is a copy") {
      val result = shell.run(
        """record R(a: String, b: String, c: String)
          |class MR {
          |public:
          |  static def mk(s: String): R = new R(s, "b", "c")
          |  static def main(args: String[]): String = ev(1000000, mk("x")).toString()
          |private:
          |  static @TailRecursive def ev(k: Int, r: R): R {
          |    if k == 0 { return r }
          |    return od(k - 1, mk(r.a()).copy(b = "E"))
          |  }
          |  static @TailRecursive def od(k: Int, r: R): R {
          |    if k == 0 { return r }
          |    return ev(k - 1, mk(r.a()).copy(c = "O"))
          |  }
          |}
          |""".stripMargin, "CopyAsTailArgument.on", Array())
      assert(Shell.Success("R(a=x, b=b, c=O)") == result)
    }
  }
}
