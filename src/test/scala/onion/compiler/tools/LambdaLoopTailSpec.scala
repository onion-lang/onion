package onion.compiler.tools

import onion.tools.Shell

/** A block lambda that ends with a `do`/`while` or a labelled loop runs that loop (#3030). */
class LambdaLoopTailSpec extends AbstractShellSpec {
  private def count(tail: String): Shell.Result = shell.run(
    s"""
       |class Test {
       |public:
       |  static def run(f: () -> void): void { f() }
       |  static def main(args: String[]): Int {
       |    var n = 0
       |    run(() -> { $tail })
       |    return n
       |  }
       |}
       |""".stripMargin,
    "LambdaLoopTail.on", Array())

  describe("a lambda ending with a loop") {
    it("runs a trailing do/while") {
      assert(Shell.Success(1) == count("do { n += 1 } while false"))
    }
    it("runs a trailing labelled while") {
      assert(Shell.Success(1) == count("var i = 0; lbl: while i < 1 { n += 1; i += 1 }"))
    }
    it("still runs a loop followed by a statement") {
      assert(Shell.Success(2) == count("do { n += 1 } while false; n += 1"))
    }
  }
}
