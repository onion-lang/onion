package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for range literals a..b / a..<b (issue #121), desugared to
 * onion.Range (Iterable[Integer]).
 */
class RangeSpec extends AbstractShellSpec {

  describe("Range literals") {
    it("iterates an inclusive range") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    var sum = 0
          |    foreach i: Int in 1..5 { sum += i }
          |    return sum
          |  }
          |}
          |""".stripMargin,
        "InclusiveRange.on",
        Array()
      )
      assert(Shell.Success(15) == result)
    }

    it("iterates an exclusive range") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    var s = ""
          |    foreach i: Int in 0..<3 { s = s + i }
          |    return s
          |  }
          |}
          |""".stripMargin,
        "ExclusiveRange.on",
        Array()
      )
      assert(Shell.Success("012") == result)
    }

    it("supports expression endpoints for index iteration") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val xs = [10, 20, 30]
          |    var total = 0
          |    foreach i: Int in 0..<xs.size() { total += xs[i] }
          |    return total
          |  }
          |}
          |""".stripMargin,
        "RangeIndexIteration.on",
        Array()
      )
      assert(Shell.Success(60) == result)
    }

    it("is a first-class value with size and contains") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val r = 2..4
          |    return r.size() + ":" + r.contains(3) + ":" + r.contains(5)
          |  }
          |}
          |""".stripMargin,
        "RangeValue.on",
        Array()
      )
      assert(Shell.Success("3:true:false") == result)
    }

    it("treats a descending range as empty") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    var count = 0
          |    foreach i: Int in 5..1 { count += 1 }
          |    return count
          |  }
          |}
          |""".stripMargin,
        "EmptyRange.on",
        Array()
      )
      assert(Shell.Success(0) == result)
    }

    it("pipelines through toList") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val sum = (1..100).toList().fold(0) { a, x => (a as Int) + (x as Int) }
          |    return (sum as Int)
          |  }
          |}
          |""".stripMargin,
        "RangePipeline.on",
        Array()
      )
      assert(Shell.Success(5050) == result)
    }

    it("does not break floating point literals") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val d = 1.5 + 0.5
          |    val e = 2.0
          |    return "" + (d == e)
          |  }
          |}
          |""".stripMargin,
        "FloatStillWorks.on",
        Array()
      )
      assert(Shell.Success("true") == result)
    }
  }
}
