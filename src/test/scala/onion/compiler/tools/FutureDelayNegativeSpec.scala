package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/** `Future::delay(deadline - Timing::millis())` completes at once when the deadline has passed (#2453). */
class FutureDelayNegativeSpec extends AnyFunSpec {

  it("delay completes immediately for a zero or negative duration") {
    for (ms <- Seq(-5L, 0L, Long.MinValue)) {
      assert(onion.Future.delay(ms).await() == null)
      val f = onion.Future.delay(ms)
      assert(f.isCompleted && f.isSuccess, s"delay($ms) should already have succeeded")
    }
  }

  it("delay still waits for a positive duration") {
    val start = System.nanoTime()
    onion.Future.delay(20L).await()
    assert(System.nanoTime() - start >= 20000000L)
  }
}
