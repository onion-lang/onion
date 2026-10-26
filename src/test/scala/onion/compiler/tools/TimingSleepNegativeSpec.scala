package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/** `Timing::sleep(deadline - Timing::millis())` returns at once when the deadline has passed (#2421). */
class TimingSleepNegativeSpec extends AnyFunSpec {

  it("sleep returns immediately for a zero or negative duration") {
    onion.Timing.sleep(0L)
    onion.Timing.sleep(-50L)
    onion.Timing.sleep(Long.MinValue)
  }

  it("sleepNanos returns immediately for a zero or negative duration") {
    onion.Timing.sleepNanos(0L)
    onion.Timing.sleepNanos(-5L)
    onion.Timing.sleepNanos(-1500000L)
    onion.Timing.sleepNanos(Long.MinValue)
  }

  it("sleepNanos still waits for a positive duration") {
    val start = System.nanoTime()
    onion.Timing.sleepNanos(2000000L)
    assert(System.nanoTime() - start >= 2000000L)
  }
}
