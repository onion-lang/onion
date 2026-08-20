package onion.compiler.tools

import onion.tools.Shell

/**
 * A `try`/`catch` expression on the right-hand side of a compound assignment
 * (`+=`, `-=`, ...) targeting a field or array element. Compound assignment
 * desugars `target op= value` to `target = target op value`
 * (`SimpleExpressionTypingSupport.typeBinaryAssignmentSimple`), so the `try`
 * ends up nested inside a `BinaryTerm` rather than directly as the assigned
 * value -- unlike the plain-assignment cases already covered by
 * `TryInFieldAssignmentSpec`/`TryInArrayIndexAssignmentSpec`. This locks in
 * that `TermContainsTry.contains` (which recurses generically through a
 * term's children) still detects the nested `try` and
 * `visitSetField`/`visitSetArray` still spill the receiver/array-and-index
 * before evaluating it, for both the field and array-element cases (the
 * `#745`/`#752` family of stack-corruption bugs).
 */
class TryInCompoundFieldArrayAssignmentSpec extends AbstractShellSpec {
  describe("try/catch expression as the RHS of a compound assignment") {
    it("does not corrupt the receiver of a field compound assignment (obj.field += try {...} catch {...})") {
      val result = shell.run(
        """
          |class Box {
          |public:
          |  var n: Int
          |  def this(initial: Int) { n = initial }
          |}
          |class Test {
          |public:
          |  static def risky(x: Int): Int {
          |    if x > 0 { return x }
          |    throw new Exception("boom")
          |  }
          |
          |  static def main(args: String[]): String {
          |    val b: Box = new Box(10)
          |    b.n += try { Test::risky(5) } catch e: Exception { 0 }
          |    val afterOk: Int = b.n
          |    b.n += try { Test::risky(-1) } catch e: Exception { 1 }
          |    return afterOk + "|" + b.n
          |  }
          |}
          |""".stripMargin,
        "TryInCompoundFieldAssignment.on",
        Array()
      )
      assert(Shell.Success("15|16") == result)
    }

    it("does not corrupt the array reference/index of an array-element compound assignment (arr[i] += try {...} catch {...})") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def risky(x: Int): Int {
          |    if x > 0 { return x }
          |    throw new Exception("boom")
          |  }
          |
          |  static def main(args: String[]): String {
          |    val arr: Int[] = new Int[2]
          |    arr[0] = 10
          |    arr[1] = 20
          |    arr[0] += try { Test::risky(5) } catch e: Exception { 0 }
          |    arr[1] += try { Test::risky(-1) } catch e: Exception { 1 }
          |    return arr[0] + "|" + arr[1]
          |  }
          |}
          |""".stripMargin,
        "TryInCompoundArrayAssignment.on",
        Array()
      )
      assert(Shell.Success("15|21") == result)
    }

    it("evaluates a side-effecting array index exactly once around the spilled temp") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static var log: String = ""
          |
          |  static def nextIndex(): Int {
          |    Test::log = Test::log + "I"
          |    return 0
          |  }
          |
          |  static def risky(): Int {
          |    Test::log = Test::log + "T"
          |    throw new Exception("boom")
          |  }
          |
          |  static def main(args: String[]): String {
          |    val arr: Int[] = new Int[1]
          |    arr[0] = 100
          |    arr[Test::nextIndex()] += try { Test::risky() } catch e: Exception { 5 }
          |    return Test::log + ":" + arr[0]
          |  }
          |}
          |""".stripMargin,
        "TryInCompoundArrayAssignmentIndexOnce.on",
        Array()
      )
      assert(Shell.Success("IT:105") == result)
    }
  }
}
