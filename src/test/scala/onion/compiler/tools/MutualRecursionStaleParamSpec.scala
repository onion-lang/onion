package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression test for #2334: inside a @TailRecursive mutual-recursion group, a
 * parameter read the optimizer did not rewrite (a lambda's capture, a `try`/`finally`,
 * a static-field assignment) saw the first call's value on every round.
 */
class MutualRecursionStaleParamSpec extends AbstractShellSpec {
  describe("@TailRecursive mutual recursion reads the current parameters") {
    it("gives each lambda its own round's parameter") {
      val script =
        """
          |class M {
          |  static var fs: java.util.List[() -> Int] = new java.util.ArrayList[() -> Int]()
          |private:
          |  static @TailRecursive def ev(n: Int): Int {
          |    if n == 0 { return 0 }
          |    fs.add(() -> n)
          |    return od(n - 1)
          |  }
          |  static @TailRecursive def od(n: Int): Int {
          |    if n == 0 { return 0 }
          |    fs.add(() -> n)
          |    return ev(n - 1)
          |  }
          |public:
          |  static def main(args: String[]): String {
          |    ev(4)
          |    var s = ""
          |    foreach f in fs { s = s + f() + " " }
          |    return s
          |  }
          |}
          |""".stripMargin
      assert(shell.run(script, "MutualStaleLambda.on", Array()) == Shell.Success("4 3 2 1 "))
    }

    it("reads the current parameter inside try/finally and a static-field assignment") {
      val script =
        """
          |class M {
          |  static var log: String = ""
          |  static var last: Int = 0
          |private:
          |  static @TailRecursive def a(n: Int): Int {
          |    if n == 0 { return 0 }
          |    try { log = log + n } finally { log = log + "b" }
          |    last = n
          |    return b(n - 1)
          |  }
          |  static @TailRecursive def b(n: Int): Int {
          |    if n == 0 { return 0 }
          |    try { log = log + n } finally { log = log + "b" }
          |    last = n
          |    return a(n - 1)
          |  }
          |public:
          |  static def main(args: String[]): String {
          |    a(4)
          |    return log + " " + last
          |  }
          |}
          |""".stripMargin
      assert(shell.run(script, "MutualStaleTry.on", Array()) == Shell.Success("4b3b2b1b 1"))
    }

    it("still runs as a loop: depth 100000 with a try in the body completes") {
      val script =
        """
          |class M {
          |  static var count: Int = 0
          |private:
          |  static @TailRecursive def a(n: Int, acc: Long): Long {
          |    if n == 0 { return acc }
          |    try { count = count + 1 } finally { }
          |    return b(n - 1, acc + n)
          |  }
          |  static @TailRecursive def b(n: Int, acc: Long): Long {
          |    if n == 0 { return acc }
          |    try { count = count + 1 } finally { }
          |    return a(n - 1, acc + n)
          |  }
          |public:
          |  static def main(args: String[]): String = "" + a(100000, 0L) + " " + count
          |}
          |""".stripMargin
      assert(shell.run(script, "MutualStaleDeep.on", Array()) == Shell.Success("5000050000 100000"))
    }
  }
}
