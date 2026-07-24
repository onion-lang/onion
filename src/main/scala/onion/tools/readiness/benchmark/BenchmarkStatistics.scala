package onion.tools.readiness.benchmark

object BenchmarkStatistics:
  def summarize(values: Vector[Long]): Either[BenchmarkFailure, BenchmarkSummary] =
    if values.isEmpty then
      Left(
        BenchmarkFailure(
          FailureCategory.InvalidMeasurement,
          "cannot summarize an empty observation set"
        )
      )
    else if values.exists(_ < 0L) then
      Left(
        BenchmarkFailure(
          FailureCategory.InvalidMeasurement,
          "cannot summarize a negative duration"
        )
      )
    else
      val sorted = values.sorted
      val median =
        if sorted.size % 2 == 1 then sorted(sorted.size / 2)
        else
          val upper = sorted(sorted.size / 2)
          val lower = sorted(sorted.size / 2 - 1)
          lower + (upper - lower) / 2L
      val rank = (95L * sorted.size + 99L) / 100L
      val p95 = sorted(rank.toInt - 1)
      Right(
        BenchmarkSummary(
          medianNanos = median,
          p95Nanos = p95,
          minNanos = sorted.head,
          maxNanos = sorted.last
        )
      )
