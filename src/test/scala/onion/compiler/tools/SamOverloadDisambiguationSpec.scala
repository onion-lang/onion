package onion.compiler.tools

import onion.tools.Shell

/**
 * A lambda passed to an overloaded SAM method (e.g. ExecutorService.submit,
 * which has both submit(Runnable) and submit(Callable[T])) is disambiguated by
 * the lambda body: a value-producing body picks the non-void SAM, a void body
 * picks the void SAM, instead of reporting an ambiguity (E0006).
 */
class SamOverloadDisambiguationSpec extends AbstractShellSpec {
  describe("SAM overload disambiguation") {
    it("resolves a value lambda to submit(Callable)") {
      val r = shell.run(
        """import { java.util.concurrent.Executors; java.util.concurrent.ExecutorService; }
          |def main(args: String[]): Int {
          |  val pool: ExecutorService = Executors::newFixedThreadPool(1)
          |  val fut = pool.submit(() -> 42)
          |  val v = fut.get()
          |  pool.shutdown()
          |  return v as Int
          |}""".stripMargin, "None", Array())
      assert(Shell.Success(42) == r)
    }
    it("resolves a void lambda to submit(Runnable)") {
      val r = shell.run(
        """import { java.util.concurrent.Executors; java.util.concurrent.ExecutorService; }
          |def main(args: String[]): String {
          |  val pool: ExecutorService = Executors::newFixedThreadPool(1)
          |  val fut = pool.submit(() -> { val x = 1 })
          |  fut.get()
          |  pool.shutdown()
          |  return "ok"
          |}""".stripMargin, "None", Array())
      assert(Shell.Success("ok") == r)
    }
    it("does not disturb a Comparator method-argument lambda") {
      val r = shell.run(
        """import { java.util.ArrayList; java.util.Collections; }
          |def main(args: String[]): Int {
          |  val xs: ArrayList[Int] = new ArrayList[Int]
          |  xs.add(3); xs.add(1); xs.add(2)
          |  Collections::sort(xs, (a: Int, b: Int) -> a - b)
          |  return xs.get(0)
          |}""".stripMargin, "None", Array())
      assert(Shell.Success(1) == r)
    }
  }
}
