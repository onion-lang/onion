package onion.compiler.tools

import onion.compiler.parser.{JJOnionParser, OnionParser}
import onion.tools.Shell

import java.io.StringReader

/**
 * A trailing lambda with no parameters needs no arrow: `Future::async { compute() }` is
 * the zero-parameter closure `{ -> compute() }`. The `{` is read as a lambda only where a
 * block could not follow the call anyway; in a condition position (the condition of `if`,
 * `while` and `do ... while`, the collection of `foreach ... in`, the scrutinee of `select`,
 * the `for` header) a bare `{` still opens the statement block, so `if flag { ... }` and
 * `foreach x in xs { ... }` keep their meaning. The arrow form `{ x -> ... }` is
 * recognised everywhere, as before.
 */
class BareTrailingLambdaSpec extends AbstractShellSpec {

  private def run(src: String, name: String): Shell.Result = shell.run(src, name, Array())

  private def parity(src: String): Unit = {
    val fast = OnionParser.parse(src)
    val jj = new JJOnionParser(new StringReader(src)).unit()
    assert(fast == jj, "fast-path AST diverged from the JavaCC AST")
  }

  describe("a trailing lambda without an arrow") {
    it("is a zero-parameter closure on a static call, with `return` or a final expression") {
      val src =
        """
          |class Test {
          |public:
          |  static def fetchUser(): Int = 20
          |  static def main(args: String[]): Int {
          |    val a: Future[Int] = Future::async { return fetchUser() }
          |    val b: Future[Int] = Future::async { 21 + 1 }
          |    val c: Future[Int] = Future::async {
          |      val base = a.await()
          |      base + b.await()
          |    }
          |    return c.await()
          |  }
          |}
          |""".stripMargin
      parity(src)
      assert(Shell.Success(42) == run(src, "BareStatic.on"))
    }

    it("attaches to instance and unqualified calls, with and without an argument list") {
      val src =
        """
          |class Runner {
          |public:
          |  def this {}
          |  def twice(f: Function0[Int]): Int = f.call() + f.call()
          |  def plus(n: Int, f: Function0[Int]): Int = n + f.call()
          |}
          |class Test {
          |public:
          |  static def once(f: Function0[Int]): Int = f.call()
          |  static def main(args: String[]): Int {
          |    val r = new Runner()
          |    return r.twice { 10 } + r.plus(1) { 2 } + once { 3 }
          |  }
          |}
          |""".stripMargin
      parity(src)
      assert(Shell.Success(26) == run(src, "BareInstance.on"))
    }

    it("keeps `{` as the statement block in every condition position") {
      val src =
        """
          |class Test {
          |public:
          |  static def flag(): Boolean = true
          |  static def main(args: String[]): Int {
          |    var n = 0
          |    val ok = true
          |    if ok { n += 1 }
          |    if flag() { n += 10 }
          |    var i = 0
          |    while i < 2 { i += 1; n += 100 }
          |    do { n += 1000 } while flag() == false
          |    val xs = [1, 2]
          |    foreach x: Int in xs { n += 10000 * x }
          |    select xs.size {
          |      case 2: n += 100000
          |      else: n += 0
          |    }
          |    for var j = 0; j < xs.size; j++ { n += 1000000 }
          |    if xs.any { x -> x > 1 } { n += 10000000 }
          |    return n
          |  }
          |}
          |""".stripMargin
      parity(src)
      assert(Shell.Success(12131211) == run(src, "BareConditions.on"))
    }

    it("is a lambda again inside parentheses or a nested block within a condition") {
      val src =
        """
          |class Test {
          |public:
          |  static def once(f: Function0[Int]): Int = f.call()
          |  static def main(args: String[]): Int {
          |    var n = 0
          |    if (once { 1 }) == 1 { n += 1 }
          |    if (Test::once { 2 }) == 2 { n += 10 }
          |    if [1, 2].any { x -> once { x } == 2 } { n += 100 }
          |    return n
          |  }
          |}
          |""".stripMargin
      parity(src)
      assert(Shell.Success(111) == run(src, "BareNested.on"))
    }
  }
}
