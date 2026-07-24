package onion.tools.readiness.benchmark

import java.util.concurrent.{
  Callable,
  ExecutionException,
  ExecutorService,
  TimeUnit,
  TimeoutException
}
import scala.util.control.NonFatal

final case class BenchmarkRunConfig(
  warmupIterations: Int = 8,
  measuredIterations: Int = 25,
  timeoutMillis: Long = 30000L
):
  require(warmupIterations >= 0, "warmup iterations must be non-negative")
  require(measuredIterations > 0, "measured iterations must be positive")
  require(timeoutMillis > 0L, "timeout must be positive")

final case class IterationPayload(
  phases: Vector[PhaseObservation],
  sourceMetrics: SourceMetrics,
  exitCode: Int
)

trait NanoClock:
  def nanoTime(): Long

object NanoClock:
  object System extends NanoClock:
    override def nanoTime(): Long = java.lang.System.nanoTime()

trait BenchmarkSession extends AutoCloseable:
  def runIteration(index: Int): IterationPayload
  override def close(): Unit = ()

trait BenchmarkScenario:
  def metadata: ScenarioMetadata
  def open(): BenchmarkSession

final case class BenchmarkScenarioException(message: String)
  extends RuntimeException(message)

final class BenchmarkEngine(
  config: BenchmarkRunConfig,
  clock: NanoClock,
  executor: ExecutorService
) extends AutoCloseable:
  def run(scenario: BenchmarkScenario): ScenarioResult =
    val warmups = Vector.newBuilder[IterationObservation]
    val measurements = Vector.newBuilder[IterationObservation]
    var failure: Option[BenchmarkFailure] = None
    var session: BenchmarkSession = null

    try
      session = scenario.open()
      var index = 0
      while index < config.warmupIterations && failure.isEmpty do
        observe(session, index, ObservationKind.Warmup) match
          case Right(observation) => warmups += observation
          case Left(problem) => failure = Some(problem)
        index += 1

      index = 0
      while index < config.measuredIterations && failure.isEmpty do
        observe(session, index, ObservationKind.Measurement) match
          case Right(observation) => measurements += observation
          case Left(problem) => failure = Some(problem)
        index += 1
    catch
      case NonFatal(error) =>
        failure = Some(
          BenchmarkFailure(
            FailureCategory.ScenarioFailure,
            messageOf(error)
          )
        )
    finally
      if session != null then
        try session.close()
        catch
          case NonFatal(error) =>
            if failure.isEmpty then
              failure = Some(
                BenchmarkFailure(
                  FailureCategory.ScenarioFailure,
                  s"scenario cleanup failed: ${messageOf(error)}"
                )
              )

    val keptWarmups = warmups.result()
    val keptMeasurements = measurements.result()
    val summarized =
      if failure.nonEmpty then Left(failure.get)
      else BenchmarkStatistics.summarize(keptMeasurements.map(_.elapsedNanos))
    val finalFailure = failure.orElse(summarized.left.toOption)
    val summary = summarized.toOption

    ScenarioResult(
      metadata = scenario.metadata,
      warmups = keptWarmups,
      measurements = keptMeasurements,
      summary = summary,
      failure = finalFailure
    )

  private def observe(
    session: BenchmarkSession,
    index: Int,
    kind: ObservationKind
  ): Either[BenchmarkFailure, IterationObservation] =
    val future = executor.submit(new Callable[IterationObservation]:
      override def call(): IterationObservation =
        val started = clock.nanoTime()
        val payload = session.runIteration(index)
        val elapsed = clock.nanoTime() - started
        if elapsed < 0L then
          throw BenchmarkScenarioException("clock produced a negative duration")
        IterationObservation(
          index = index,
          kind = kind,
          elapsedNanos = elapsed,
          phases = payload.phases,
          sourceMetrics = payload.sourceMetrics,
          exitCode = payload.exitCode
        )
    )

    try Right(future.get(config.timeoutMillis, TimeUnit.MILLISECONDS))
    catch
      case _: TimeoutException =>
        future.cancel(true)
        Left(
          BenchmarkFailure(
            FailureCategory.ScenarioFailure,
            s"iteration timed out after ${config.timeoutMillis} ms",
            Some(index)
          )
        )
      case error: ExecutionException =>
        Left(
          BenchmarkFailure(
            FailureCategory.ScenarioFailure,
            messageOf(Option(error.getCause).getOrElse(error)),
            Some(index)
          )
        )
      case _: InterruptedException =>
        future.cancel(true)
        Thread.currentThread().interrupt()
        Left(
          BenchmarkFailure(
            FailureCategory.ScenarioFailure,
            "benchmark thread was interrupted",
            Some(index)
          )
        )

  private def messageOf(error: Throwable): String =
    Option(error.getMessage)
      .filter(_.nonEmpty)
      .getOrElse(error.getClass.getSimpleName)

  override def close(): Unit =
    executor.shutdownNow()
