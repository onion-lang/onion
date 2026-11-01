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

    it("does not treat a same-signature call on a different instance as self-recursion") {
      // Regression: isSelfCall matched name/class/argument-types only, so a tail call
      // to the same method on a *different* receiver (e.g. next.sumAcc(...) while
      // walking a linked structure) was misidentified as self-recursion. optimizeMethod
      // then rewrote the body into a while(true) loop that only ever updated this
      // method's own parameter slots, never advancing to the new receiver, so the
      // loop's exit condition could never become true -- an infinite loop/hang.
      val result = shell.run(
        """
          |class Node {
          |  val value: Int
          |  val next: Node?
          |public:
          |  def this(value: Int, next: Node?) { this.value = value; this.next = next }
          |  final def sumAcc(acc: Int): Int {
          |    if next == null { return acc + value }
          |    return next.sumAcc(acc + value)
          |  }
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val n1 = new Node(1, new Node(2, new Node(3, null)))
          |    return n1.sumAcc(0)
          |  }
          |}
          |""".stripMargin,
        "TcoDifferentReceiverNotSelfCall.on",
        Array()
      )
      assert(Shell.Success(6) == result)
    }

    it("correctly computes @TailRecursive mutual recursion with a Long accumulator parameter") {
      // Regression: rewriteParameterReferences.rewriteTerm fell through to `case _ => term`
      // for AsInstanceOf nodes, so widening Int k to Long inside `acc + k` left the original
      // k parameter slot unrewritten. The state machine read k from slot 0 (original param)
      // instead of the loop variable slot, giving wrong results.
      val result = shell.run(
        """
          |class AltAcc {
          |private:
          |  @TailRecursive
          |  def addOdd(k: Int, acc: Long): Long {
          |    if k <= 0 { return acc }
          |    return addEven(k - 1, acc + k)
          |  }
          |  @TailRecursive
          |  def addEven(k: Int, acc: Long): Long {
          |    if k <= 0 { return acc }
          |    return addOdd(k - 1, acc - k)
          |  }
          |public:
          |  def compute(n: Int): Long = addOdd(n, 0L)
          |}
          |
          |class Main {
          |public:
          |  static def main(args: String[]): Long = new AltAcc().compute(3)
          |}
          |""".stripMargin,
        "MutualTCOLongAcc.on",
        Array()
      )
      // alternatingSum(3) = 3 - 2 + 1 = 2
      assert(Shell.Success(2L) == result)
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
  

    // #2329: the loop read a stale parameter wherever the old rewrite did not reach.
    it("reads the current parameter inside try/catch in a tail-recursive method") {
      val result = shell.run(
        """
          |class C {
          |public:
          |  static def d(n: Int, acc: String): String {
          |    if n == 0 { return acc }
          |    var s = acc
          |    try { s = s + n } catch e: Exception { }
          |    return d(n - 1, s)
          |  }
          |  static def main(args: String[]): String = d(3, "")
          |}
          |""".stripMargin,
        "TcoTryParam.on",
        Array()
      )
      assert(Shell.Success("321") == result)
    }

    it("reads the current parameter inside try/finally and try-with-resources") {
      val result = shell.run(
        """
          |class R conforms AutoCloseable {
          |  val name: String
          |public:
          |  def this(name: String) { this.name = name }
          |  def get(): String = name
          |  def close(): void { }
          |}
          |class C {
          |public:
          |  static def f(n: Int, acc: String): String {
          |    if n == 0 { return acc }
          |    var s = acc
          |    try { s = s + n } finally { s = s + "." }
          |    try (val r = new R("t" + n)) { s = s + r.get() }
          |    return f(n - 1, s)
          |  }
          |  static def main(args: String[]): String = f(2, "")
          |}
          |""".stripMargin,
        "TcoTryResourceParam.on",
        Array()
      )
      assert(Shell.Success("2.t21.t1") == result)
    }

    it("lets a lambda capture the parameter value of its own iteration") {
      val result = shell.run(
        """
          |class C {
          |public:
          |  static def go(n: Int, fs: java.util.List[() -> Int]): Int {
          |    if n == 0 { return 0 }
          |    fs.add(() -> n)
          |    return go(n - 1, fs)
          |  }
          |  static def main(args: String[]): String {
          |    val fs = new java.util.ArrayList[() -> Int]()
          |    go(3, fs)
          |    var s = ""
          |    foreach f in fs { s = s + f() }
          |    return s
          |  }
          |}
          |""".stripMargin,
        "TcoLambdaParam.on",
        Array()
      )
      assert(Shell.Success("321") == result)
    }

    it("reads the current parameter in a static-field assignment") {
      val result = shell.run(
        """
          |class C {
          |public:
          |  static var log: String = ""
          |  static def g(n: Int): Int {
          |    if n == 0 { return 0 }
          |    log = log + n
          |    return g(n - 1)
          |  }
          |  static def main(args: String[]): String {
          |    g(3)
          |    return log
          |  }
          |}
          |""".stripMargin,
        "TcoStaticFieldParam.on",
        Array()
      )
      assert(Shell.Success("321") == result)
    }

    it("still loops (no stack overflow) when the body reads a parameter inside try") {
      val result = shell.run(
        """
          |class C {
          |public:
          |  static def count(n: Int, acc: Int): Int {
          |    if n == 0 { return acc }
          |    var step = 0
          |    try { step = n - (n - 1) } catch e: Exception { }
          |    return count(n - 1, acc + step)
          |  }
          |  static def main(args: String[]): Int = count(100000, 0)
          |}
          |""".stripMargin,
        "TcoTryDeep.on",
        Array()
      )
      assert(Shell.Success(100000) == result)
    }
  }

  describe("tail-call optimization of an expression body (#2330)") {
    it("loops for `= if c { acc } else { f(..) }` in a static method") {
      val result = shell.run(
        """
          |class C {
          |public:
          |  static def go(n: Int, acc: Int): Int = if n == 0 { acc } else { go(n - 1, acc + 1) }
          |  static def main(args: String[]): Int = go(1000000, 0)
          |}
          |""".stripMargin,
        "TcoExprIf.on",
        Array()
      )
      assert(Shell.Success(1000000) == result)
    }

    it("loops through an else-if chain and a default argument in a top-level function") {
      val result = shell.run(
        """
          |def walk(n: Int, acc: Long = 0L): Long =
          |  if n == 0 { acc } else if n % 2 == 0 { walk(n - 1, acc + 2L) } else { walk(n - 1, acc + 1L) }
          |val total: Long = walk(1000000)
          |if total != 1500000L { throw new IllegalStateException("walk returned " + total) }
          |""".stripMargin,
        "TcoExprElseIf.on",
        Array()
      )
      assert(result.isInstanceOf[Shell.Success], s"expected success, got $result")
    }

    it("loops for a select in tail position, with a destructuring case and a guard") {
      val result = shell.run(
        """
          |record P(a: Int, b: Int)
          |class C {
          |public:
          |  static def go(p: P, acc: Long): Long = select p {
          |    case P(a, b) when a == 0: acc
          |    case P(a, b) when a % 2 == 0: go(new P(a - 1, b), acc + b)
          |    case P(a, b): go(new P(a - 1, b), acc + 1L)
          |  }
          |  static def main(args: String[]): Long = go(new P(1000000, 3), 0L)
          |}
          |""".stripMargin,
        "TcoExprSelect.on",
        Array()
      )
      assert(Shell.Success(2000000L) == result)
    }

    it("keeps a self call that is not the branch's last value a real call") {
      // `{ val x = f(n - 1); x + 1 }` and `f(0, acc) + 100` use the call's result,
      // so turning them into a jump would return the wrong value.
      val result = shell.run(
        """
          |class C {
          |public:
          |  static def depth(n: Int): Int = if n == 0 { 0 } else { val x = depth(n - 1); x + 1 }
          |  static def mixed(n: Int, acc: Int): Int =
          |    if n == 0 { acc } else if n == 1 { mixed(0, acc) + 100 } else { mixed(n - 1, acc + 1) }
          |  static def main(args: String[]): String = depth(5) + "," + mixed(5, 0)
          |}
          |""".stripMargin,
        "TcoExprNonTail.on",
        Array()
      )
      assert(Shell.Success("5,104") == result)
    }

    it("does not drop the last store to a `var` a lambda captured") {
      // `y = if .. { g(..) } else { .. }; y`: the closure must see the stored value.
      val result = shell.run(
        """
          |class C {
          |public:
          |  static var last: Function0[Int]? = null
          |  static def g(n: Int): Int = {
          |    var y = -1
          |    val k: () -> Int = () -> y
          |    if n == 3 { C::last = k }
          |    y = if n > 0 { g(n - 1) } else { 100 }
          |    y
          |  }
          |  static def main(args: String[]): String = {
          |    val a = g(3)
          |    val k = C::last!!
          |    a + "," + k()
          |  }
          |}
          |""".stripMargin,
        "TcoExprCapturedVar.on",
        Array()
      )
      assert(Shell.Success("100,100") == result)
    }
  }

  describe("tail-call optimization of a select case with bindings (#2350)") {
    def run(name: String, code: String) = shell.run(code.stripMargin, name, Array())

    it("loops through an unguarded record destructuring case") {
      val result = run("TcoSelectDestructure.on",
        """
          |record P(a: Int, b: Int)
          |class C {
          |public:
          |  static def go(p: P, acc: Int): Int = select p {
          |    case P(a, b) when a == 0: acc
          |    case P(a, b): go(new P(a - 1, b), acc + b)
          |  }
          |  static def main(args: String[]): Int = go(new P(1000000, 1), 0)
          |}
          |""")
      assert(Shell.Success(1000000) == result)
    }

    it("loops through a type pattern case") {
      val result = run("TcoSelectTypePattern.on",
        """
          |class C {
          |public:
          |  static def go(o: Object, n: Int, acc: Int): Int = select o {
          |    case i is Integer: if n == 0 { acc } else { go(o, n - 1, acc + 1) }
          |    else: acc
          |  }
          |  static def main(args: String[]): Int = go(1, 1000000, 0)
          |}
          |""")
      assert(Shell.Success(1000000) == result)
    }
  }

  describe("tail-call optimization next to a boxed branch (#2406)") {
    def run(name: String, code: String) = shell.run(code.stripMargin, name, Array())

    it("loops when the other branch is an Integer unboxed to the Int result") {
      val result = run("TcoBoxedBranch.on",
        """
          |class C {
          |public:
          |  static def q(o: Integer, n: Int): Int = if n == 0 { o } else { q(o, n - 1) }
          |  static def main(args: String[]): Int = q(7, 1000000)
          |}
          |""")
      assert(Shell.Success(7) == result)
    }

    it("still throws NullPointerException for a null Integer in the taken branch") {
      val result = run("TcoBoxedBranchNull.on",
        """
          |class C {
          |public:
          |  static def q(o: Integer, n: Int): Int = if n == 0 { o } else { q(o, n - 1) }
          |  static def main(args: String[]): Int {
          |    try { return q(null, 3) } catch e: NullPointerException { return -1 }
          |  }
          |}
          |""")
      assert(Shell.Success(-1) == result)
    }

    it("leaves the unboxing of an if under a try outside the branches, like a bare call") {
      // The panel's repro on #2413: a `try` around `if c { nn() }` must let the unboxing
      // NullPointerException escape, as a `try` around a bare `nn()` does.
      val result = run("TcoBoxedBranchTry.on",
        """
          |class C {
          |public:
          |  static def nn(): Integer = null
          |  static def a(): Int {
          |    val x: Int = try { nn() } catch e: NullPointerException { -1 }
          |    return x
          |  }
          |  static def d(c: Boolean): Int {
          |    val x: Int = try { if c { nn() } else { 1 } } catch e: NullPointerException { -1 }
          |    return x
          |  }
          |  static def main(args: String[]): Int {
          |    var r: Int = 0
          |    try { a() } catch e: NullPointerException { r = r + 1 }
          |    try { d(true) } catch e: NullPointerException { r = r + 10 }
          |    return r + d(false) * 100
          |  }
          |}
          |""")
      assert(Shell.Success(111) == result)
    }

    it("loops through an else-if chain with a boxed Long branch") {
      val result = run("TcoBoxedBranchLong.on",
        """
          |class C {
          |public:
          |  static def q(o: JLong, n: Int): Long =
          |    if n == 0 { o } else if n == -1 { 0L } else { q(o, n - 1) }
          |  static def main(args: String[]): Int = q(5L, 1000000) as Int
          |}
          |""")
      assert(Shell.Success(5) == result)
    }
  }
}
