package onion.compiler.tools

import onion.tools.Shell

/**
 * End-to-end miscompilation guard: dense programs that exercise many codegen
 * paths (collections, records, recursion, tail-call, generics, closures,
 * labeled loops, nullable, string interpolation) and return a hand-verified
 * result. A wrong result means the compiler mis-generated code for some path.
 */
class CodegenCorrectnessSpec extends AbstractShellSpec {
  describe("codegen correctness") {
    it("list map/filter/fold pipeline") {
      val r = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val xs: List[Int] = [1, 2, 3, 4, 5]
          |    val doubled = xs.map { x => (x as Int) * 2 }
          |    val evens = doubled.filter { x => (x as Int) % 4 == 0 }
          |    return evens.fold(0) { a, b => (a as Int) + (b as Int) }
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(12) == r)
    }

    it("records and fold with a static helper") {
      val r = shell.run(
        """
          |record Item(name: String, price: Int, qty: Int)
          |class Test {
          |public:
          |  static def lineTotal(i: Item): Int = i.price() * i.qty()
          |  static def main(args: String[]): Int {
          |    val items: List[Item] = [new Item("a", 100, 3), new Item("b", 50, 2)]
          |    return items.fold(0) { acc, i => (acc as Int) + Test::lineTotal(i as Item) }
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(400) == r)
    }

    it("recursion (fib) plus tail-recursion (sumTo)") {
      val r = shell.run(
        """
          |class Test {
          |public:
          |  static def fib(n: Int): Int = if n < 2 { n } else { Test::fib(n-1) + Test::fib(n-2) }
          |  static def sumTo(acc: Int, n: Int): Int = if n == 0 { acc } else { Test::sumTo(acc + n, n - 1) }
          |  static def main(args: String[]): Int { return Test::fib(10) + Test::sumTo(0, 100) }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(5105) == r)
    }

    it("map with foreach (k, v) destructuring") {
      val r = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val m: Map[String, Int] = ["a": 1, "b": 2, "c": 3]
          |    var total: Int = 0
          |    foreach (k, v) in m { total = total + (v as Int) }
          |    return total + m.size
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(9) == r)
    }

    it("generic Box[T]") {
      val r = shell.run(
        """
          |class Box[T] {
          |  val value: T
          |public:
          |  def this(v: T) { this.value = v }
          |  def get(): T = value
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val b: Box[Int] = new Box[Int](21)
          |    return (b.get() as Int) * 2
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(42) == r)
    }

    it("string interpolation and methods") {
      val r = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val name = "onion"
          |    val n = name.length()
          |    return "#{name}-#{n}-#{name.toUpperCase()}"
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("onion-5-ONION") == r)
    }

    it("nested loops with labeled break") {
      val r = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    var count: Int = 0
          |    outer: for var i: Int = 0; i < 5; i = i + 1 {
          |      for var j: Int = 0; j < 5; j = j + 1 {
          |        if i * j > 6 { break outer }
          |        count = count + 1
          |      }
          |    }
          |    return count
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(14) == r)
    }

    it("nullable, smart cast, and target-typed empty literal") {
      val r = shell.run(
        """
          |class Test {
          |public:
          |  static def firstOrNull(xs: List[String]): String? {
          |    if xs.size() > 0 { return xs.get(0) } else { return null }
          |  }
          |  static def main(args: String[]): Int {
          |    val one: List[String] = ["x", "y"]
          |    val none: List[String] = []
          |    val a = Test::firstOrNull(one)
          |    val b = Test::firstOrNull(none)
          |    var r: Int = 0
          |    if a != null { r = r + a.length() }
          |    if b == null { r = r + 100 }
          |    return r
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(101) == r)
    }

    it("tail-call optimization avoids stack overflow at 100000 depth") {
      val r = shell.run(
        """
          |class Test {
          |public:
          |  static def count(acc: Int, n: Int): Int = if n == 0 { acc } else { Test::count(acc + 1, n - 1) }
          |  static def main(args: String[]): Int { return Test::count(0, 100000) }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(100000) == r)
    }

    it("throw-only lambda with inferred return type compiles and throws when invoked") {
      val r = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val f = () -> { throw new RuntimeException("boom") }
          |    var caught: Int = 0
          |    try { f.call() } catch e: Exception { caught = 1 }
          |    return caught
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(1) == r)
    }

    it("value-returning closure with an early return and a void fall-through") {
      val r = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val cb: (Object) -> Object = (x: Object) -> {
          |      if x == null { return null }
          |      IO::println("processing")
          |    }
          |    val r1 = cb.call("a")
          |    val r2 = cb.call(null)
          |    var count: Int = 0
          |    if r1 == null { count = count + 1 }
          |    if r2 == null { count = count + 1 }
          |    return count
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(2) == r)
    }
  }
}
