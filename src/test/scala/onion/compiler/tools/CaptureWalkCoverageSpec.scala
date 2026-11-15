package onion.compiler.tools

import onion.tools.Shell

/**
 * A lambda writes through to the `var`s it captures wherever it appears: inside a `?.`
 * call, a `do ... while` loop, a labelled loop, a `select` guard, a `for` initializer or a
 * try-with-resources declaration. Before #3024 the capture scan never looked inside these
 * nodes, so the variable was copied instead of shared and every write was lost.
 */
class CaptureWalkCoverageSpec extends AbstractShellSpec {
  private def runMain(body: String, file: String): Shell.Result =
    shell.run(
      s"""
         |class Test {
         |public:
         |  static def run(g: () -> void): void { g() }
         |  static def bump(g: () -> Int): Int = g()
         |  static def main(args: String[]): String {
         |$body
         |  }
         |}
         |""".stripMargin, file, Array())

  describe("a lambda that writes to a captured var") {
    it("keeps its writes inside a ?. call (the issue's repro)") {
      val result = runMain(
        """    val xs: List[Int]? = [1, 2, 3]
          |    var s = 0
          |    xs?.forEach((x: Int) -> { s += x })
          |    return "safe=" + s""".stripMargin, "CaptureSafeCall.on")
      assert(result == Shell.Success("safe=6"))
    }

    it("keeps its writes inside a do/while loop") {
      val result = runMain(
        """    var d = 0
          |    do { run(() -> { d += 1 }) } while d < 0
          |    return "do=" + d""".stripMargin, "CaptureDoWhile.on")
      assert(result == Shell.Success("do=1"))
    }

    it("keeps its writes inside a labelled loop, so the loop ends") {
      val result = runMain(
        """    var l = 0
          |    var guard = 0
          |    outer: while l < 1 && guard < 5 { guard += 1; run(() -> { l += 1 }) }
          |    return "labeled=" + l + " guard=" + guard""".stripMargin, "CaptureLabeled.on")
      assert(result == Shell.Success("labeled=1 guard=1"))
    }

    it("keeps its writes inside the target of a ?. member read") {
      val result = runMain(
        """    var n = 0
          |    val s: String? = "abc"
          |    val len = s?.length
          |    bump(() -> { n += 1; return n })
          |    val t: String? = if n > 0 { "x" } else { null }
          |    val u = (if bump(() -> { n += 1; return n }) > 0 { t } else { null })?.length
          |    return "n=" + n + " len=" + len + " u=" + u""".stripMargin, "CaptureSafeMember.on")
      assert(result == Shell.Success("n=2 len=3 u=1"))
    }

    it("keeps its writes inside a select guard") {
      val result = runMain(
        """    var g = 0
          |    val r = select 3 {
          |      case x when bump(() -> { g += x; return g }) > 0: "hit"
          |      else: "miss"
          |    }
          |    return r + " g=" + g""".stripMargin, "CaptureSelectGuard.on")
      assert(result == Shell.Success("hit g=3"))
    }

    it("keeps its writes inside a for initializer") {
      val result = runMain(
        """    var f = 0
          |    for var i: Int = bump(() -> { f += 10; return 0 }); i < 2; i++ { }
          |    return "for=" + f""".stripMargin, "CaptureForInit.on")
      assert(result == Shell.Success("for=10"))
    }

    it("keeps its writes inside a try-with-resources declaration") {
      val result = runMain(
        """    var t = 0
          |    try (val r = new java.io.StringReader("" + bump(() -> { t += 7; return t }))) {
          |      r.read()
          |    }
          |    return "try=" + t""".stripMargin, "CaptureTryResource.on")
      assert(result == Shell.Success("try=7"))
    }
  }
}
