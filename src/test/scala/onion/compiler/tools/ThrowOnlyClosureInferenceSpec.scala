package onion.compiler.tools

import onion.tools.Shell

/**
 * A closure that never completes normally (`() -> { throw ... }`) infers a
 * bottom return, so a generic call over it is not pinned to `Object`
 * (issue #314): `Future::async(() -> { throw ... })` is a `Future[Nothing]`,
 * and `await()` on it is assignable anywhere.
 *
 * Bottom is not a usable JVM return type, so the *synthesized closure method*
 * still returns `Object`. These tests therefore assert on executed behavior,
 * not just compilation: the earlier attempts at this fix each compiled and then
 * failed the class verifier.
 */
class ThrowOnlyClosureInferenceSpec extends AbstractShellSpec {

  it("assigns the awaited result of a throw-only async to a primitive") {
    // Future[Object] would reject this at compile time (E0000).
    val r = shell.run(
      """import { onion.Future; }
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val f = Future::async(() -> { throw new RuntimeException("boom") })
        |    var caught: Int = 0
        |    try { val b: Int = f.await() } catch e: Exception { caught = 7 }
        |    return caught
        |  }
        |}
        |""".stripMargin, "None", Array())
    assert(Shell.Success(7) == r)
  }

  it("still compiles and runs a bare throw-only closure invoked for effect") {
    // The value is discarded, so the emitted code must pop what the erased call
    // actually leaves on the stack.
    val r = shell.run(
      """class Test {
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

  it("assigns a throw-only closure result to a reference target") {
    val r = shell.run(
      """class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val f = () -> { throw new RuntimeException("boom") }
        |    var caught: Int = 0
        |    try { val s: String = f.call() } catch e: Exception { caught = 2 }
        |    return caught
        |  }
        |}
        |""".stripMargin, "None", Array())
    assert(Shell.Success(2) == r)
  }

  it("still honors an explicit target type over the inferred bottom") {
    val r = shell.run(
      """import { onion.Future; }
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val f: Future[Int] = Future::async(() -> { throw new RuntimeException("boom") })
        |    var caught: Int = 0
        |    try { val b: Int = f.await() } catch e: Exception { caught = 3 }
        |    return caught
        |  }
        |}
        |""".stripMargin, "None", Array())
    assert(Shell.Success(3) == r)
  }

  it("leaves a value-returning closure inferring its real type") {
    val r = shell.run(
      """import { onion.Future; }
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val f = Future::async(() -> { return 40 })
        |    return f.await() + 2
        |  }
        |}
        |""".stripMargin, "None", Array())
    assert(Shell.Success(42) == r)
  }

  it("leaves a null-returning closure widening to Object") {
    // Only the never-returns case keeps bottom; a null-producing body still
    // widens, since Object is its honest static type.
    val r = shell.run(
      """class Test {
        |public:
        |  static def main(args: String[]): String {
        |    val f = () -> { return null }
        |    val v = f.call()
        |    return "" + v
        |  }
        |}
        |""".stripMargin, "None", Array())
    assert(Shell.Success("null") == r)
  }
}
