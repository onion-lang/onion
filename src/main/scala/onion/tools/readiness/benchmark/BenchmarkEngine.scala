package onion.tools.readiness.benchmark

import java.util.concurrent.{
  Callable,
  ExecutionException,
  ExecutorService,
  Executors,
  ThreadFactory,
  TimeUnit,
  TimeoutException
}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
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

object BenchmarkExecutor:
  private val threadCount = new AtomicInteger()

  def daemonSingleThread(): ExecutorService =
    Executors.newSingleThreadExecutor(new ThreadFactory:
      override def newThread(runnable: Runnable): Thread =
        val thread = new Thread(
          runnable,
          s"onion-benchmark-${threadCount.incrementAndGet()}"
        )
        thread.setDaemon(true)
        thread
    )

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
  private final case class ObservationProblem(
    failure: BenchmarkFailure,
    cleanupDeferred: Boolean
  )

  private final class SessionCloser(session: BenchmarkSession):
    private val closed = new AtomicBoolean(false)

    def close(): Option[Throwable] =
      if closed.compareAndSet(false, true) then
        try
          session.close()
          None
        catch case NonFatal(error) => Some(error)
      else None

  private final class IterationControl(closer: SessionCloser):
    private val closeOnExit = new AtomicBoolean(false)
    private val finished = new AtomicBoolean(false)

    def deferSessionClose(): Unit =
      closeOnExit.set(true)
      if finished.get() then closer.close()

    def markFinished(): Unit =
      finished.set(true)
      if closeOnExit.get() then closer.close()

  private final case class BenchmarkObservationException(
    category: FailureCategory,
    detail: String
  ) extends RuntimeException(detail)

  def run(scenario: BenchmarkScenario): ScenarioResult =
    val warmups = Vector.newBuilder[IterationObservation]
    val measurements = Vector.newBuilder[IterationObservation]
    var failure: Option[BenchmarkFailure] = None
    var session: BenchmarkSession = null
    var closer: SessionCloser = null
    var cleanupDeferred = false

    try
      session = scenario.open()
      closer = new SessionCloser(session)
      var index = 0
      while index < config.warmupIterations && failure.isEmpty do
        observe(session, closer, index, ObservationKind.Warmup) match
          case Right(observation) => warmups += observation
          case Left(problem) =>
            failure = Some(problem.failure)
            cleanupDeferred = problem.cleanupDeferred
        index += 1

      index = 0
      while index < config.measuredIterations && failure.isEmpty do
        observe(session, closer, index, ObservationKind.Measurement) match
          case Right(observation) => measurements += observation
          case Left(problem) =>
            failure = Some(problem.failure)
            cleanupDeferred = problem.cleanupDeferred
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
      if closer != null && !cleanupDeferred then
        closer.close().foreach { error =>
          if failure.isEmpty then
            failure = Some(
              BenchmarkFailure(
                FailureCategory.ScenarioFailure,
                s"scenario cleanup failed: ${messageOf(error)}"
              )
            )
        }

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
    closer: SessionCloser,
    index: Int,
    kind: ObservationKind
  ): Either[ObservationProblem, IterationObservation] =
    val control = new IterationControl(closer)
    val future = executor.submit(new Callable[IterationObservation]:
      override def call(): IterationObservation =
        try
          val started = clock.nanoTime()
          val payload = session.runIteration(index)
          val elapsed = clock.nanoTime() - started
          validate(elapsed, payload)
          IterationObservation(
            index = index,
            kind = kind,
            elapsedNanos = elapsed,
            phases = payload.phases,
            sourceMetrics = payload.sourceMetrics,
            exitCode = payload.exitCode
          )
        finally control.markFinished()
    )

    try Right(future.get(config.timeoutMillis, TimeUnit.MILLISECONDS))
    catch
      case _: TimeoutException =>
        control.deferSessionClose()
        future.cancel(true)
        Left(
          ObservationProblem(
            BenchmarkFailure(
              FailureCategory.ScenarioFailure,
              s"iteration timed out after ${config.timeoutMillis} ms",
              Some(index)
            ),
            cleanupDeferred = true
          )
        )
      case error: ExecutionException =>
        val cause = Option(error.getCause).getOrElse(error)
        val (category, message) = cause match
          case problem: BenchmarkObservationException =>
            (problem.category, problem.detail)
          case _ =>
            (FailureCategory.ScenarioFailure, messageOf(cause))
        Left(
          ObservationProblem(
            BenchmarkFailure(category, message, Some(index)),
            cleanupDeferred = false
          )
        )
      case _: InterruptedException =>
        control.deferSessionClose()
        future.cancel(true)
        Thread.currentThread().interrupt()
        Left(
          ObservationProblem(
            BenchmarkFailure(
              FailureCategory.ScenarioFailure,
              "benchmark thread was interrupted",
              Some(index)
            ),
            cleanupDeferred = true
          )
        )

  private def validate(elapsed: Long, payload: IterationPayload): Unit =
    if elapsed < 0L then
      throw BenchmarkObservationException(
        FailureCategory.InvalidMeasurement,
        "clock produced a negative duration"
      )
    if payload.exitCode != 0 then
      throw BenchmarkObservationException(
        FailureCategory.ScenarioFailure,
        s"scenario returned exit code ${payload.exitCode}"
      )
    val metrics = payload.sourceMetrics
    if
      metrics.sourceCount < 0 ||
      metrics.lineCount < 0 ||
      metrics.byteCount < 0L ||
      metrics.generatedClasses < 0
    then
      throw BenchmarkObservationException(
        FailureCategory.InvalidMeasurement,
        "scenario returned negative source metrics"
      )
    if
      payload.phases.exists { phase =>
        phase.elapsedNanos < 0L ||
        phase.inputCount < 0 ||
        phase.outputCount < 0
      }
    then
      throw BenchmarkObservationException(
        FailureCategory.InvalidMeasurement,
        "scenario returned negative phase measurements"
      )

  private def messageOf(error: Throwable): String =
    Option(error.getMessage)
      .filter(_.nonEmpty)
      .getOrElse(error.getClass.getSimpleName)

  override def close(): Unit =
    executor.shutdownNow()
