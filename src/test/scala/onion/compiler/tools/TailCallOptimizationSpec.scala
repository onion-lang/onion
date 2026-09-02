package onion.compiler.tools

import onion.tools.Shell

/**
 * Tail-call optimization rewrites direct self-recursion in non-overridable
 * methods (private, static, or final) into a loop. A depth of 100000 would
 * overflow the stack without it.
 */
class TailCallOptimizationSpec extends AbstractShellSpec {
  describe("tail-call optimization") {
    it("optimizes a static method (no stack overflow at depth 100000)") {
      val result = shell.run(
        """
          |class C {
          |public:
          |  static def count(n: Int, acc: Int): Int {
          |    if n == 0 { return acc }
          |    return count(n - 1, acc + 1)
          |  }
          |  static def main(args: String[]): Int = count(100000, 0)
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(100000) == result)
    }

    it("optimizes a top-level function") {
      val result = shell.run(
        """
          |def count(n: Int, acc: Int): Int {
          |  if n == 0 { return acc }
          |  return count(n - 1, acc + 1)
          |}
          |IO::println(count(100000, 0))
          |""".stripMargin,
        "None",
        Array()
      )
      assert(result.isInstanceOf[Shell.Success])
    }

    it("does not crash on a recursive function with a typed-List parameter and indexed access") {
      // Regression: TCO placed loop variables at TypedAST indices paramCount..2*paramCount-1,
      // but those slots are already taken by body-local variables (val x, val next, ...).
      // The resulting slot collision caused an internal BytecodeGeneration error.
      val result = shell.run(
        """
          |record Item(value: Int)
          |
          |def sumValues(items: List[Item], idx: Int, acc: Int): Int {
          |  if idx >= items.size { return acc }
          |  val x = items[idx] as Item
          |  return sumValues(items, idx + 1, acc + x.value())
          |}
          |
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    val list: List[Item] = [new Item(1), new Item(2), new Item(3)]
          |    return "" + sumValues(list, 0, 0)
          |  }
          |}
          |""".stripMargin,
        "TcoTypedListIndex.on",
        Array()
      )
      assert(Shell.Success("6") == result)
    }

    it("optimizes a @TailRecursive mutual-recursion group (no stack overflow at depth 1000000)") {
      // MutualRecursionOptimization rewrites an all-private, matching-signature
      // @TailRecursive group into a state machine (see
      // src/main/scala/onion/compiler/optimization/MutualRecursionOptimization.scala).
      // IneffectiveTailRecursiveWarningSpec only checks that W0016 fires or not at
      // compile time; this checks the optimized bytecode is actually correct at runtime.
      val result = shell.run(
        """
          |class Parity {
          |private:
          |  @TailRecursive
          |  def isEven(n: Int): Boolean {
          |    if n == 0 { return true }
          |    return isOdd(n - 1)
          |  }
          |  @TailRecursive
          |  def isOdd(n: Int): Boolean {
          |    if n == 0 { return false }
          |    return isEven(n - 1)
          |  }
          |public:
          |  def check(n: Int): Boolean = isEven(n)
          |}
          |
          |class Main {
          |public:
          |  static def main(args: String[]): Boolean = new Parity().check(1000000)
          |}
          |""".stripMargin,
        "MutualTailRecursiveDepth.on",
        Array()
      )
      assert(Shell.Success(true) == result)
    }

    it("optimizes a zero-argument static method without crashing") {
      val result = shell.run(
        """
          |class C {
          |public:
          |  static var count: Int = 0
          |  static def loop(): Int {
          |    if count >= 100000 { return count }
          |    count = count + 1
          |    return loop()
          |  }
          |  static def main(args: String[]): Int {
          |    count = 0
          |    return loop()
          |  }
          |}
          |""".stripMargin,
        "ZeroArgStaticTCO.on",
        Array()
      )
      assert(Shell.Success(100000) == result)
    }
  }
}
