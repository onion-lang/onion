package onion.compiler.tools

import onion.tools.Shell

/**
 * ExecutorService.invokeAll(tasks) where tasks: List[Callable[T]] must resolve
 * (issue #274). The JDK signature is
 *   <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
 * so the method type variable T is inferred through a wildcard-bounded
 * collection argument (Collection<? extends Callable<T>>). Method applicability
 * previously ignored a method type variable buried inside a wildcard bound, so
 * the formal defaulted to its erased Object bound and the call failed with
 * E0005 (no applicable method). This spec guards the fix so wildcard-bounded,
 * method-inferred type variables (same family as #256/#259) keep resolving.
 */
class InvokeAllWildcardInferenceSpec extends AbstractShellSpec {
  it("resolves invokeAll over a List[Callable[Int]] and runs the tasks") {
    val result = shell.run(
      """
        |import {
        |  java.util.*
        |  java.util.concurrent.*
        |}
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val pool = Executors::newFixedThreadPool(2)
        |    val tasks = new ArrayList[Callable[Int]]
        |    tasks.add((() -> 10) as Callable[Int])
        |    tasks.add((() -> 20) as Callable[Int])
        |    val results = pool.invokeAll(tasks)
        |    var sum = 0
        |    var i = 0
        |    while i < results.size() {
        |      val f = results.get(i) as java.util.concurrent.Future
        |      sum = sum + (f.get() as JInteger).intValue()
        |      i = i + 1
        |    }
        |    pool.shutdown()
        |    return sum
        |  }
        |}
        |""".stripMargin,
      "InvokeAllWildcard.on",
      Array()
    )
    assert(Shell.Success(30) == result)
  }
}
