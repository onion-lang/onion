package onion.compiler.tools

import onion.tools.Shell

/**
 * Top-level `val`/`var` declarations are translated local-first -- so smart-cast /
 * null narrowing works on them within `start`, exactly like inside a function -- and
 * mirrored into a field of the script's synthetic class (#165) so top-level functions
 * and later statements can still see them. The field is initialised in order; a `val`
 * declared after a function that uses it (forward reference) is still not visible.
 */
class TopLevelValSpec extends AbstractShellSpec {
  describe("top-level val/var visibility") {
    it("lets a top-level function read a top-level val") {
      val result = shell.run(
        """
          |val counter = 100
          |def getIt(): Int = counter
          |IO::println(getIt())
          |""".stripMargin,
        "None",
        Array()
      )
      assert(result.isInstanceOf[Shell.Success])
    }

    it("lets a top-level function reassign a top-level var") {
      val result = shell.run(
        """
          |var total = 0
          |def add(n: Int): void { total = total + n }
          |add(10)
          |add(20)
          |IO::println(total)
          |""".stripMargin,
        "None",
        Array()
      )
      assert(result.isInstanceOf[Shell.Success])
    }

    it("supports a memoizing recursive function over a top-level cache") {
      val result = shell.run(
        """
          |import { java.util.HashMap }
          |val cache = new HashMap[Int, Long]()
          |def fib(n: Int): Long {
          |  if n <= 1 { return n as Long }
          |  if cache.containsKey(n) { return cache.get(n) }
          |  val r = fib(n - 1) + fib(n - 2)
          |  cache.put(n, r)
          |  return r
          |}
          |IO::println(fib(50))
          |""".stripMargin,
        "None",
        Array()
      )
      assert(result.isInstanceOf[Shell.Success])
    }

    it("initialises dependent top-level vals in order") {
      val result = shell.run(
        """
          |val a: Int = 5
          |val b = a * 2
          |def sum(): Int = a + b
          |IO::println(sum())
          |""".stripMargin,
        "None",
        Array()
      )
      assert(result.isInstanceOf[Shell.Success])
    }

    it("does not see a val declared after the function (forward reference)") {
      val result = shell.run(
        """
          |def getIt(): Int = counter
          |val counter = 100
          |IO::println(getIt())
          |""".stripMargin,
        "None",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }

    it("smart-casts a top-level val after a null check (#165 local-first)") {
      val result = shell.run(
        """
          |val a: String? = "hello"
          |if a != null {
          |  IO::println(a.length)
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(result.isInstanceOf[Shell.Success])
    }
  }
}
