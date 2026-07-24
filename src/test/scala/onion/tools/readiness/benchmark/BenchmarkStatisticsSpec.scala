package onion.tools.readiness.benchmark

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.OptionValues

class BenchmarkStatisticsSpec extends AnyFunSpec with Matchers with OptionValues:
  describe("BenchmarkStatistics.summarize"):
    it("calculates median and nearest-rank p95 without dropping observations"):
      val result = BenchmarkStatistics.summarize(Vector(50L, 10L, 40L, 20L, 30L))
      result shouldBe Right(
        BenchmarkSummary(
          medianNanos = 30L,
          p95Nanos = 50L,
          minNanos = 10L,
          maxNanos = 50L
        )
      )

    it("uses the mean of the middle pair for an even observation count"):
      BenchmarkStatistics.summarize(Vector(7L, 11L)) shouldBe Right(
        BenchmarkSummary(9L, 11L, 7L, 11L)
      )

    it("rejects an empty observation set as an invalid measurement"):
      val result = BenchmarkStatistics.summarize(Vector.empty)
      result.left.toOption.value.category shouldBe FailureCategory.InvalidMeasurement

    it("rejects a negative duration"):
      val result = BenchmarkStatistics.summarize(Vector(1L, -1L))
      result.left.toOption.value.message should include ("negative")
