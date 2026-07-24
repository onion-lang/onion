package onion.tools.readiness.benchmark

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.OptionValues

import java.util.concurrent.{CountDownLatch, Executors}

class BenchmarkEngineSpec extends AnyFunSpec with Matchers with OptionValues:
  private final class SequenceClock(values: Seq[Long]) extends NanoClock:
    private val iterator = values.iterator
    override def nanoTime(): Long = iterator.next()

  private def metadata =
    ScenarioMetadata("steady-fresh:test", ScenarioKind.SteadyFresh, "test", "hash")

  private def payload =
    IterationPayload(
      phases = Vector.empty,
      sourceMetrics = SourceMetrics(1, 1, 1L, 1),
      exitCode = 0
    )

  describe("BenchmarkEngine"):
    it("keeps warmups separate and summarizes only measurements"):
      var calls = 0
      val scenario = new BenchmarkScenario:
        override val metadata: ScenarioMetadata = BenchmarkEngineSpec.this.metadata
        override def open(): BenchmarkSession = new BenchmarkSession:
          override def runIteration(index: Int): IterationPayload =
            calls += 1
            payload

      val executor = Executors.newSingleThreadExecutor()
      val engine = new BenchmarkEngine(
        BenchmarkRunConfig(1, 2, 1000L),
        new SequenceClock(Seq(0L, 5L, 5L, 12L, 12L, 23L)),
        executor
      )
      val result = try engine.run(scenario) finally engine.close()

      calls shouldBe 3
      result.warmups.map(_.elapsedNanos) shouldBe Vector(5L)
      result.measurements.map(_.elapsedNanos) shouldBe Vector(7L, 11L)
      result.summary shouldBe Some(BenchmarkSummary(9L, 11L, 7L, 11L))
      result.failure shouldBe None

    it("returns completed observations when a later iteration fails"):
      var calls = 0
      val scenario = new BenchmarkScenario:
        override val metadata: ScenarioMetadata = BenchmarkEngineSpec.this.metadata
        override def open(): BenchmarkSession = new BenchmarkSession:
          override def runIteration(index: Int): IterationPayload =
            calls += 1
            if calls == 3 then throw BenchmarkScenarioException("compiler failed")
            payload

      val executor = Executors.newSingleThreadExecutor()
      val engine = new BenchmarkEngine(
        BenchmarkRunConfig(1, 2, 1000L),
        new SequenceClock(Seq(0L, 1L, 1L, 3L, 3L)),
        executor
      )
      val result = try engine.run(scenario) finally engine.close()

      result.warmups.size shouldBe 1
      result.measurements.size shouldBe 1
      result.summary shouldBe None
      result.failure.value.category shouldBe FailureCategory.ScenarioFailure
      result.failure.value.iteration shouldBe Some(1)

    it("marks an iteration timeout as a scenario failure"):
      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val scenario = new BenchmarkScenario:
        override val metadata: ScenarioMetadata = BenchmarkEngineSpec.this.metadata
        override def open(): BenchmarkSession = new BenchmarkSession:
          override def runIteration(index: Int): IterationPayload =
            entered.countDown()
            release.await()
            payload

      val executor = Executors.newSingleThreadExecutor()
      val engine = new BenchmarkEngine(
        BenchmarkRunConfig(0, 1, 25L),
        NanoClock.System,
        executor
      )
      val result =
        try engine.run(scenario)
        finally
          release.countDown()
          engine.close()

      entered.getCount shouldBe 0L
      result.failure.value.message should include ("timed out")
      result.measurements shouldBe empty
