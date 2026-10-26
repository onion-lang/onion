package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression for #2361: a lambda inside a constructor that assigns a captured
 * `var` local must share that variable with the constructor body, as it does
 * inside a method. The constructor used to capture the local by value, so the
 * lambda's write was silently lost.
 */
class ConstructorClosureCaptureSpec extends AbstractShellSpec {

  describe("a lambda in a constructor") {
    it("writes a captured var local back to the constructor (#2361)") {
      val result = shell.run(
        """
          |class C {
          |  var v: Int
          |public:
          |  def this(m: Int) {
          |    var n = m
          |    val f: () -> Int = () -> { n = n * 3; n }
          |    val r = f()
          |    v = n * 100 + r
          |  }
          |  def get: Int = v
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int = new C(2).get
          |}
          |""".stripMargin,
        "CtorClosureVar.on",
        Array()
      )
      assert(Shell.Success(606) == result)
    }

    it("sees a write the constructor makes after the lambda is created") {
      val result = shell.run(
        """
          |class C {
          |  var v: String
          |public:
          |  def this {
          |    var s = "a"
          |    val f: () -> String = () -> s
          |    s = s + "b"
          |    v = f()
          |  }
          |  def get: String = v
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String = new C().get
          |}
          |""".stripMargin,
        "CtorClosureLaterWrite.on",
        Array()
      )
      assert(Shell.Success("ab") == result)
    }

    it("keeps working when the constructor delegates with this(...)") {
      val result = shell.run(
        """
          |class C {
          |  var tag: String
          |  var count: Int
          |public:
          |  def this(t: String) { tag = t }
          |  def this : this("x") {
          |    var k = 0
          |    val inc: () -> Int = () -> { k = k + 1; k }
          |    inc(); inc()
          |    count = k
          |  }
          |  def get: String = tag + count
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String = new C().get
          |}
          |""".stripMargin,
        "CtorClosureDelegate.on",
        Array()
      )
      assert(Shell.Success("x2") == result)
    }
  }
}
