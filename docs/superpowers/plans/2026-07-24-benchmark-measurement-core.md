# Benchmark Measurement Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Onion's ambiguous average-only benchmark with a tested,
versioned measurement core that reports raw observations, robust statistics,
phase profiles, environment metadata, and realistic steady-state compiler
workloads.

**Architecture:** A small benchmark domain model is independent of execution,
statistics, compiler scenarios, and rendering. `BenchmarkEngine` owns lifecycle,
warmup, timeout, and failure semantics through injectable clocks and sessions.
The existing `onion.tools.BenchmarkRunner` becomes a compatibility entry point
over the new implementation, and `sbt bench` remains available beside the
clearer `sbt benchmark` task.

**Tech Stack:** Scala 3.3.7, Java 17 APIs, ScalaTest 3.2.19, Onion's existing
compiler pipeline and `onion.Json`, sbt 1.12.1.

## Global Constraints

- Use Scala 3 indentation style with two spaces and no tabs.
- Add no third-party dependencies.
- Store every duration as integer nanoseconds; convert only while rendering.
- Never label a scenario `cold` or `warm` unless its lifecycle implements that
  exact meaning.
- Keep failed and timed-out observations visible; never substitute zero or
  discard an outlier.
- Use 8 warmups, 25 measurements, and a 30-second iteration timeout by default.
- Preserve `sbt 'bench --iterations N --json'` as a supported invocation.
- Default JSON output is `target/readiness/benchmark-v1.json`.
- This plan implements steady-state fresh-compiler scenarios only. The
  process-cold, persistent-REPL, and generated multi-file protocols are the
  next performance plan and must use the interfaces defined here.
- Before execution, confirm `java -version` reports JDK 17 or later and
  `sbt --version` can launch sbt 1.12.1. Install missing tools outside the
  repository; do not commit tool binaries.

---

## File Map

### New production files

- `src/main/scala/onion/tools/readiness/benchmark/BenchmarkModel.scala`
  owns wire-stable enums and immutable result types.
- `src/main/scala/onion/tools/readiness/benchmark/BenchmarkStatistics.scala`
  owns median and nearest-rank p95 calculations.
- `src/main/scala/onion/tools/readiness/benchmark/BenchmarkEngine.scala`
  owns warmup, measurement, timeout, partial-result, and session lifecycle.
- `src/main/scala/onion/tools/readiness/benchmark/CompileWorkload.scala`
  loads source metadata and computes stable workload hashes.
- `src/main/scala/onion/tools/readiness/benchmark/FreshCompileScenario.scala`
  adapts `OnionCompiler.compileDetailed` to the benchmark lifecycle.
- `src/main/scala/onion/tools/readiness/benchmark/BenchmarkMetadata.scala`
  captures Git and JVM/OS environment identity.
- `src/main/scala/onion/tools/readiness/benchmark/BenchmarkReport.scala`
  owns schema-versioned performance report types.
- `src/main/scala/onion/tools/readiness/benchmark/BenchmarkRender.scala`
  owns JSON and text rendering.
- `src/main/scala/onion/tools/readiness/benchmark/BenchmarkOptions.scala`
  parses and validates CLI options without exiting the JVM.

### Modified production files

- `src/main/scala/onion/tools/BenchmarkRunner.scala` becomes the thin
  compatibility CLI.
- `build.sbt` adds the `benchmark` input task while retaining `bench`.
- `docs/contributing/building.md` documents scenario semantics and artifacts.
- `docs/ja/contributing/building.md` mirrors the command documentation.

### New test files

- `src/test/scala/onion/tools/readiness/benchmark/BenchmarkStatisticsSpec.scala`
- `src/test/scala/onion/tools/readiness/benchmark/BenchmarkEngineSpec.scala`
- `src/test/scala/onion/tools/readiness/benchmark/FreshCompileScenarioSpec.scala`
- `src/test/scala/onion/tools/readiness/benchmark/BenchmarkRenderSpec.scala`
- `src/test/scala/onion/tools/readiness/benchmark/BenchmarkOptionsSpec.scala`

---

### Task 1: Immutable Model and Robust Statistics

**Files:**

- Create:
  `src/main/scala/onion/tools/readiness/benchmark/BenchmarkModel.scala`
- Create:
  `src/main/scala/onion/tools/readiness/benchmark/BenchmarkStatistics.scala`
- Test:
  `src/test/scala/onion/tools/readiness/benchmark/BenchmarkStatisticsSpec.scala`

**Interfaces:**

- Consumes: no project-specific interfaces.
- Produces:
  `ScenarioKind`, `ObservationKind`, `FailureCategory`, `SourceMetrics`,
  `PhaseObservation`, `IterationObservation`, `BenchmarkSummary`,
  `BenchmarkFailure`, `ScenarioMetadata`, `ScenarioResult`, and
  `BenchmarkStatistics.summarize(values: Vector[Long])`.

- [ ] **Step 1: Write the failing statistics tests**

Create `BenchmarkStatisticsSpec.scala`:

```scala
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
```

- [ ] **Step 2: Run the new suite and verify it fails**

Run:

```bash
sbt 'testOnly onion.tools.readiness.benchmark.BenchmarkStatisticsSpec'
```

Expected: compilation fails because `BenchmarkStatistics`,
`BenchmarkSummary`, and `FailureCategory` do not exist.

- [ ] **Step 3: Add the immutable benchmark model**

Create `BenchmarkModel.scala`:

```scala
package onion.tools.readiness.benchmark

enum ScenarioKind(val wireName: String):
  case ProcessCold extends ScenarioKind("process-cold")
  case SteadyFresh extends ScenarioKind("steady-fresh")
  case PersistentSession extends ScenarioKind("persistent-session")
  case MultiFile extends ScenarioKind("multi-file")

enum ObservationKind(val wireName: String):
  case Warmup extends ObservationKind("warmup")
  case Measurement extends ObservationKind("measurement")

enum FailureCategory(val wireName: String):
  case InvalidMeasurement extends FailureCategory("invalid-measurement")
  case ScenarioFailure extends FailureCategory("scenario-failure")
  case UnstableEnvironment extends FailureCategory("unstable-environment")
  case BudgetFailure extends FailureCategory("budget-failure")

final case class SourceMetrics(
  sourceCount: Int,
  lineCount: Int,
  byteCount: Long,
  generatedClasses: Int
)

final case class PhaseObservation(
  name: String,
  elapsedNanos: Long,
  inputCount: Int,
  outputCount: Int
)

final case class IterationObservation(
  index: Int,
  kind: ObservationKind,
  elapsedNanos: Long,
  phases: Vector[PhaseObservation],
  sourceMetrics: SourceMetrics,
  exitCode: Int
)

final case class BenchmarkSummary(
  medianNanos: Long,
  p95Nanos: Long,
  minNanos: Long,
  maxNanos: Long
)

final case class BenchmarkFailure(
  category: FailureCategory,
  message: String,
  iteration: Option[Int] = None
)

final case class ScenarioMetadata(
  id: String,
  kind: ScenarioKind,
  workload: String,
  workloadHash: String
)

final case class ScenarioResult(
  metadata: ScenarioMetadata,
  warmups: Vector[IterationObservation],
  measurements: Vector[IterationObservation],
  summary: Option[BenchmarkSummary],
  failure: Option[BenchmarkFailure]
):
  def succeeded: Boolean = failure.isEmpty
```

- [ ] **Step 4: Implement statistics with explicit invalid-input results**

Create `BenchmarkStatistics.scala`:

```scala
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
```

- [ ] **Step 5: Run the suite and verify it passes**

Run:

```bash
sbt 'testOnly onion.tools.readiness.benchmark.BenchmarkStatisticsSpec'
```

Expected: 4 tests pass.

- [ ] **Step 6: Commit the model and statistics**

```bash
git add \
  src/main/scala/onion/tools/readiness/benchmark/BenchmarkModel.scala \
  src/main/scala/onion/tools/readiness/benchmark/BenchmarkStatistics.scala \
  src/test/scala/onion/tools/readiness/benchmark/BenchmarkStatisticsSpec.scala
git commit -m "Add benchmark statistics model"
```

---

### Task 2: Lifecycle-Aware Measurement Engine

**Files:**

- Create:
  `src/main/scala/onion/tools/readiness/benchmark/BenchmarkEngine.scala`
- Test:
  `src/test/scala/onion/tools/readiness/benchmark/BenchmarkEngineSpec.scala`

**Interfaces:**

- Consumes: every model type from Task 1.
- Produces:
  `BenchmarkRunConfig`, `IterationPayload`, `NanoClock`,
  `BenchmarkSession`, `BenchmarkScenario`, `BenchmarkScenarioException`, and
  `BenchmarkEngine.run(scenario: BenchmarkScenario): ScenarioResult`.

- [ ] **Step 1: Write lifecycle, partial-failure, and timeout tests**

Create `BenchmarkEngineSpec.scala`:

```scala
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
```

- [ ] **Step 2: Run the suite and verify it fails**

Run:

```bash
sbt 'testOnly onion.tools.readiness.benchmark.BenchmarkEngineSpec'
```

Expected: compilation fails because the engine interfaces do not exist.

- [ ] **Step 3: Implement the engine and its injectable boundaries**

Create `BenchmarkEngine.scala`:

```scala
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
```

- [ ] **Step 4: Run engine and statistics tests**

Run:

```bash
sbt 'testOnly onion.tools.readiness.benchmark.BenchmarkEngineSpec onion.tools.readiness.benchmark.BenchmarkStatisticsSpec'
```

Expected: 7 tests pass.

- [ ] **Step 5: Commit the lifecycle engine**

```bash
git add \
  src/main/scala/onion/tools/readiness/benchmark/BenchmarkEngine.scala \
  src/test/scala/onion/tools/readiness/benchmark/BenchmarkEngineSpec.scala
git commit -m "Add lifecycle-aware benchmark engine"
```

---

### Task 3: Realistic Fresh-Compiler Workloads

**Files:**

- Create:
  `src/main/scala/onion/tools/readiness/benchmark/CompileWorkload.scala`
- Create:
  `src/main/scala/onion/tools/readiness/benchmark/FreshCompileScenario.scala`
- Test:
  `src/test/scala/onion/tools/readiness/benchmark/FreshCompileScenarioSpec.scala`

**Interfaces:**

- Consumes: `BenchmarkScenario`, `BenchmarkSession`, `IterationPayload`,
  `ScenarioMetadata`, `SourceMetrics`, and `PhaseObservation`.
- Produces:
  `CompileWorkload.fromFiles`, `FreshCompileScenario`, and
  `CompileScenarioCatalog.default(repoRoot)`.

- [ ] **Step 1: Write workload and compiler-adapter tests**

Create `FreshCompileScenarioSpec.scala`:

```scala
package onion.tools.readiness.benchmark

import onion.compiler.{CompilerConfig, WarningLevel}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.util.concurrent.Executors

class FreshCompileScenarioSpec extends AnyFunSpec with Matchers:
  private def config =
    CompilerConfig(
      classPath = Seq("."),
      superClass = "",
      encoding = "UTF-8",
      outputDirectory = "",
      maxErrorReports = 20,
      warningLevel = WarningLevel.Off
    )

  describe("CompileWorkload"):
    it("counts sources, lines, bytes, and hashes file contents deterministically"):
      val root = Files.createTempDirectory("onion-benchmark-workload")
      Files.writeString(root.resolve("One.on"), "val one = 1\n", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("Two.on"), "val two = 2", StandardCharsets.UTF_8)

      val first = CompileWorkload.fromFiles(
        root,
        "fixture",
        "fixture",
        Vector("One.on", "Two.on")
      )
      val second = CompileWorkload.fromFiles(
        root,
        "fixture",
        "fixture",
        Vector("One.on", "Two.on")
      )

      first.sourceCount shouldBe 2
      first.lineCount shouldBe 2
      first.byteCount should be > 0L
      first.workloadHash shouldBe second.workloadHash

  describe("FreshCompileScenario"):
    it("creates a fresh compiler result with phase and source metadata"):
      val root = Files.createTempDirectory("onion-benchmark-compile")
      Files.writeString(
        root.resolve("Hello.on"),
        """IO::println("hello")""",
        StandardCharsets.UTF_8
      )
      val workload =
        CompileWorkload.fromFiles(root, "hello", "hello", Vector("Hello.on"))
      val scenario = new FreshCompileScenario(workload, config)
      val executor = Executors.newSingleThreadExecutor()
      val engine = new BenchmarkEngine(
        BenchmarkRunConfig(0, 1, 30000L),
        NanoClock.System,
        executor
      )
      val result = try engine.run(scenario) finally engine.close()

      result.failure shouldBe None
      result.measurements should have size 1
      result.measurements.head.phases.map(_.name) should contain ("Parsing")
      result.measurements.head.phases.map(_.name) should contain ("BytecodeGeneration")
      result.measurements.head.sourceMetrics.generatedClasses should be > 0

  describe("CompileScenarioCatalog"):
    it("uses the actual practical small, medium, and large scripts"):
      val scenarios = CompileScenarioCatalog.default(Paths.get(".").toAbsolutePath.normalize())
      scenarios.map(_.metadata.id) shouldBe Vector(
        "steady-fresh:onionc:hello",
        "steady-fresh:onionc:todo-manager",
        "steady-fresh:onionc:stats-app"
      )
      scenarios.last.metadata.kind shouldBe ScenarioKind.SteadyFresh
      scenarios.last.metadata.workload shouldBe "run/StatsApp.on"
```

- [ ] **Step 2: Run the suite and verify it fails**

Run:

```bash
sbt 'testOnly onion.tools.readiness.benchmark.FreshCompileScenarioSpec'
```

Expected: compilation fails because the workload and scenario adapters do not
exist.

- [ ] **Step 3: Implement source loading and stable hashing**

Create `CompileWorkload.scala`:

```scala
package onion.tools.readiness.benchmark

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest

final case class CompileWorkload(
  id: String,
  label: String,
  root: Path,
  relativeFiles: Vector[String],
  sourceCount: Int,
  lineCount: Int,
  byteCount: Long,
  workloadHash: String
):
  def paths: Vector[Path] = relativeFiles.map(root.resolve)

object CompileWorkload:
  def fromFiles(
    root: Path,
    id: String,
    label: String,
    relativeFiles: Vector[String]
  ): CompileWorkload =
    require(relativeFiles.nonEmpty, "compile workload requires at least one source")
    val normalizedRoot = root.toAbsolutePath.normalize()
    val digest = MessageDigest.getInstance("SHA-256")
    var lines = 0
    var bytes = 0L

    relativeFiles.foreach { relative =>
      val path = normalizedRoot.resolve(relative).normalize()
      require(path.startsWith(normalizedRoot), s"workload source escapes root: $relative")
      require(Files.isRegularFile(path), s"workload source does not exist: $relative")
      val content = Files.readAllBytes(path)
      digest.update(relative.getBytes(StandardCharsets.UTF_8))
      digest.update(0.toByte)
      digest.update(content)
      bytes += content.length
      lines += countLines(new String(content, StandardCharsets.UTF_8))
    }

    CompileWorkload(
      id = id,
      label = label,
      root = normalizedRoot,
      relativeFiles = relativeFiles,
      sourceCount = relativeFiles.size,
      lineCount = lines,
      byteCount = bytes,
      workloadHash = digest.digest().map(byte => f"${byte & 0xff}%02x").mkString
    )

  private def countLines(content: String): Int =
    if content.isEmpty then 0
    else content.count(_ == '\n') + (if content.endsWith("\n") then 0 else 1)
```

- [ ] **Step 4: Implement the fresh-compiler scenario and catalog**

Create `FreshCompileScenario.scala`:

```scala
package onion.tools.readiness.benchmark

import onion.compiler.{
  CompilerConfig,
  FileInputSource,
  OnionCompiler,
  WarningLevel
}

final class FreshCompileScenario(
  workload: CompileWorkload,
  compilerConfig: CompilerConfig
) extends BenchmarkScenario:
  override val metadata: ScenarioMetadata =
    ScenarioMetadata(
      id = s"steady-fresh:onionc:${workload.id}",
      kind = ScenarioKind.SteadyFresh,
      workload = workload.label,
      workloadHash = workload.workloadHash
    )

  override def open(): BenchmarkSession = new BenchmarkSession:
    override def runIteration(index: Int): IterationPayload =
      val sources = workload.paths.map(path => new FileInputSource(path.toString))
      val result = new OnionCompiler(compilerConfig).compileDetailed(sources)
      if result.hasErrors then
        val messages = result.allErrors.map(_.message).mkString("; ")
        throw BenchmarkScenarioException(
          s"compilation failed for ${workload.label}: $messages"
        )
      IterationPayload(
        phases = result.timings.map { phase =>
          PhaseObservation(
            name = phase.name,
            elapsedNanos = phase.elapsedNanos,
            inputCount = phase.inputCount,
            outputCount = phase.outputCount
          )
        }.toVector,
        sourceMetrics = SourceMetrics(
          sourceCount = workload.sourceCount,
          lineCount = workload.lineCount,
          byteCount = workload.byteCount,
          generatedClasses = result.classes.size
        ),
        exitCode = 0
      )

object CompileScenarioCatalog:
  def default(repoRoot: java.nio.file.Path): Vector[BenchmarkScenario] =
    val config = CompilerConfig(
      classPath = Seq("."),
      superClass = "",
      encoding = "UTF-8",
      outputDirectory = "",
      maxErrorReports = 20,
      warningLevel = WarningLevel.Off
    )
    Vector(
      CompileWorkload.fromFiles(
        repoRoot,
        "hello",
        "run/Hello.on",
        Vector("run/Hello.on")
      ),
      CompileWorkload.fromFiles(
        repoRoot,
        "todo-manager",
        "run/TodoManager.on",
        Vector("run/TodoManager.on")
      ),
      CompileWorkload.fromFiles(
        repoRoot,
        "stats-app",
        "run/StatsApp.on",
        Vector("run/StatsApp.on")
      )
    ).map(workload => new FreshCompileScenario(workload, config))
```

- [ ] **Step 5: Run all measurement-core suites**

Run:

```bash
sbt 'testOnly onion.tools.readiness.benchmark.BenchmarkStatisticsSpec onion.tools.readiness.benchmark.BenchmarkEngineSpec onion.tools.readiness.benchmark.FreshCompileScenarioSpec'
```

Expected: 10 tests pass.

- [ ] **Step 6: Commit the compiler workload adapter**

```bash
git add \
  src/main/scala/onion/tools/readiness/benchmark/CompileWorkload.scala \
  src/main/scala/onion/tools/readiness/benchmark/FreshCompileScenario.scala \
  src/test/scala/onion/tools/readiness/benchmark/FreshCompileScenarioSpec.scala
git commit -m "Add practical compiler benchmark scenarios"
```

---

### Task 4: Versioned Report and Renderers

**Files:**

- Create:
  `src/main/scala/onion/tools/readiness/benchmark/BenchmarkMetadata.scala`
- Create:
  `src/main/scala/onion/tools/readiness/benchmark/BenchmarkReport.scala`
- Create:
  `src/main/scala/onion/tools/readiness/benchmark/BenchmarkRender.scala`
- Test:
  `src/test/scala/onion/tools/readiness/benchmark/BenchmarkRenderSpec.scala`

**Interfaces:**

- Consumes: `BenchmarkRunConfig` and `ScenarioResult`.
- Produces:
  `GitMetadata.capture`, `EnvironmentMetadata.capture`,
  `PerformanceBenchmarkReport.create`, `BenchmarkRender.json`, and
  `BenchmarkRender.text`.

- [ ] **Step 1: Write schema and rendering tests**

Create `BenchmarkRenderSpec.scala`:

```scala
package onion.tools.readiness.benchmark

import onion.Json
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class BenchmarkRenderSpec extends AnyFunSpec with Matchers:
  private val scenario =
    ScenarioResult(
      ScenarioMetadata(
        "steady-fresh:onionc:hello",
        ScenarioKind.SteadyFresh,
        "run/Hello.on",
        "abc"
      ),
      warmups = Vector.empty,
      measurements = Vector(
        IterationObservation(
          0,
          ObservationKind.Measurement,
          1000000L,
          Vector(PhaseObservation("Parsing", 100L, 1, 1)),
          SourceMetrics(1, 1, 10L, 1),
          0
        )
      ),
      summary = Some(BenchmarkSummary(1000000L, 1000000L, 1000000L, 1000000L)),
      failure = None
    )

  private val report =
    PerformanceBenchmarkReport(
      schemaVersion = 1,
      generatedAt = "2026-07-24T00:00:00Z",
      git = GitMetadata("deadbeef", dirty = false),
      environment = EnvironmentMetadata(
        javaVendor = "Eclipse Adoptium",
        javaVersion = "21",
        osName = "Linux",
        osArch = "amd64",
        processors = 2,
        maxHeapBytes = 2147483648L,
        garbageCollectors = Vector("G1 Young Generation", "G1 Old Generation"),
        jvmArguments = Vector("-Xmx2g")
      ),
      runConfig = BenchmarkRunConfig(0, 1, 30000L),
      scenarios = Vector(scenario),
      failures = Vector.empty
    )

  describe("BenchmarkRender"):
    it("writes schema-versioned JSON with raw nanosecond observations"):
      val json = BenchmarkRender.json(report)
      Json.parseOrNull(json) should not be null
      json should include ("\"schemaVersion\": 1")
      json should include ("\"elapsedNanos\": 1000000")
      json should include ("\"kind\": \"steady-fresh\"")
      json should not include "elapsedMillis"

    it("renders a concise human summary in milliseconds"):
      val text = BenchmarkRender.text(report)
      text should include ("steady-fresh:onionc:hello")
      text should include ("median=1.00ms")
      text should include ("p95=1.00ms")
      text should include ("1 measured")
```

- [ ] **Step 2: Run the suite and verify it fails**

Run:

```bash
sbt 'testOnly onion.tools.readiness.benchmark.BenchmarkRenderSpec'
```

Expected: compilation fails because report and renderer types do not exist.

- [ ] **Step 3: Add Git and environment metadata capture**

Create `BenchmarkMetadata.scala`:

```scala
package onion.tools.readiness.benchmark

import java.lang.management.ManagementFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Path

final case class GitMetadata(commit: String, dirty: Boolean)

object GitMetadata:
  def capture(repoRoot: Path): Either[String, GitMetadata] =
    for
      commit <- command(repoRoot, "git", "rev-parse", "HEAD")
      status <- command(repoRoot, "git", "status", "--porcelain")
    yield GitMetadata(commit.trim, status.trim.nonEmpty)

  private def command(repoRoot: Path, args: String*): Either[String, String] =
    val process =
      new ProcessBuilder(args*)
        .directory(repoRoot.toFile)
        .redirectErrorStream(true)
        .start()
    val output =
      try new String(process.getInputStream.readAllBytes(), StandardCharsets.UTF_8)
      finally process.getInputStream.close()
    val exit = process.waitFor()
    if exit == 0 then Right(output)
    else Left(s"${args.mkString(" ")} failed with exit $exit: ${output.trim}")

final case class EnvironmentMetadata(
  javaVendor: String,
  javaVersion: String,
  osName: String,
  osArch: String,
  processors: Int,
  maxHeapBytes: Long,
  garbageCollectors: Vector[String],
  jvmArguments: Vector[String]
)

object EnvironmentMetadata:
  def capture(): EnvironmentMetadata =
    import scala.jdk.CollectionConverters.*
    EnvironmentMetadata(
      javaVendor = System.getProperty("java.vendor"),
      javaVersion = System.getProperty("java.version"),
      osName = System.getProperty("os.name"),
      osArch = System.getProperty("os.arch"),
      processors = Runtime.getRuntime.availableProcessors(),
      maxHeapBytes = Runtime.getRuntime.maxMemory(),
      garbageCollectors =
        ManagementFactory.getGarbageCollectorMXBeans.asScala
          .map(_.getName)
          .toVector,
      jvmArguments =
        ManagementFactory.getRuntimeMXBean.getInputArguments.asScala.toVector
    )
```

- [ ] **Step 4: Add the versioned report model**

Create `BenchmarkReport.scala`:

```scala
package onion.tools.readiness.benchmark

import java.time.Instant

final case class PerformanceBenchmarkReport(
  schemaVersion: Int,
  generatedAt: String,
  git: GitMetadata,
  environment: EnvironmentMetadata,
  runConfig: BenchmarkRunConfig,
  scenarios: Vector[ScenarioResult],
  failures: Vector[BenchmarkFailure]
):
  def succeeded: Boolean = failures.isEmpty && scenarios.forall(_.succeeded)

object PerformanceBenchmarkReport:
  val CurrentSchemaVersion = 1

  def create(
    git: GitMetadata,
    environment: EnvironmentMetadata,
    runConfig: BenchmarkRunConfig,
    scenarios: Vector[ScenarioResult],
    failures: Vector[BenchmarkFailure] = Vector.empty
  ): PerformanceBenchmarkReport =
    PerformanceBenchmarkReport(
      schemaVersion = CurrentSchemaVersion,
      generatedAt = Instant.now().toString,
      git = git,
      environment = environment,
      runConfig = runConfig,
      scenarios = scenarios,
      failures = failures
    )
```

- [ ] **Step 5: Implement complete JSON and text rendering**

Create `BenchmarkRender.scala`:

```scala
package onion.tools.readiness.benchmark

import onion.Json

import java.util.{ArrayList, LinkedHashMap, List as JList, Map as JMap}
import scala.jdk.CollectionConverters.*

object BenchmarkRender:
  def json(report: PerformanceBenchmarkReport): String =
    Json.stringifyPretty(reportObject(report))

  def text(report: PerformanceBenchmarkReport): String =
    val lines = Vector.newBuilder[String]
    lines += s"benchmark schema=${report.schemaVersion} commit=${report.git.commit}"
    report.failures.foreach { failure =>
      lines += s"  FAILED [${failure.category.wireName}]: ${failure.message}"
    }
    report.scenarios.foreach { scenario =>
      scenario.summary match
        case Some(summary) =>
          lines += f"  ${scenario.metadata.id}%-40s median=${millis(summary.medianNanos)}%.2fms p95=${millis(summary.p95Nanos)}%.2fms ${scenario.measurements.size}%d measured"
        case None =>
          val message = scenario.failure.map(_.message).getOrElse("missing summary")
          lines += s"  ${scenario.metadata.id} FAILED: $message"
    }
    lines.result().mkString(System.lineSeparator())

  private def reportObject(report: PerformanceBenchmarkReport): JMap[String, Object] =
    obj(
      "schemaVersion" -> Int.box(report.schemaVersion),
      "generatedAt" -> report.generatedAt,
      "commit" -> report.git.commit,
      "dirty" -> Boolean.box(report.git.dirty),
      "environment" -> obj(
        "javaVendor" -> report.environment.javaVendor,
        "javaVersion" -> report.environment.javaVersion,
        "osName" -> report.environment.osName,
        "osArch" -> report.environment.osArch,
        "processors" -> Int.box(report.environment.processors),
        "maxHeapBytes" -> Long.box(report.environment.maxHeapBytes),
        "garbageCollectors" -> arr(
          report.environment.garbageCollectors.map(value => value: Object)
        ),
        "jvmArguments" -> arr(
          report.environment.jvmArguments.map(value => value: Object)
        )
      ),
      "runConfig" -> obj(
        "warmupIterations" -> Int.box(report.runConfig.warmupIterations),
        "measuredIterations" -> Int.box(report.runConfig.measuredIterations),
        "timeoutMillis" -> Long.box(report.runConfig.timeoutMillis)
      ),
      "scenarios" -> arr(report.scenarios.map(scenarioObject)),
      "failures" -> arr(report.failures.map(failureObject))
    )

  private def scenarioObject(result: ScenarioResult): Object =
    obj(
      "id" -> result.metadata.id,
      "kind" -> result.metadata.kind.wireName,
      "workload" -> result.metadata.workload,
      "workloadHash" -> result.metadata.workloadHash,
      "warmups" -> arr(result.warmups.map(observationObject)),
      "measurements" -> arr(result.measurements.map(observationObject)),
      "summary" -> result.summary.map(summaryObject).orNull,
      "failure" -> result.failure.map(failureObject).orNull
    )

  private def observationObject(value: IterationObservation): Object =
    obj(
      "index" -> Int.box(value.index),
      "kind" -> value.kind.wireName,
      "elapsedNanos" -> Long.box(value.elapsedNanos),
      "exitCode" -> Int.box(value.exitCode),
      "sourceMetrics" -> obj(
        "sourceCount" -> Int.box(value.sourceMetrics.sourceCount),
        "lineCount" -> Int.box(value.sourceMetrics.lineCount),
        "byteCount" -> Long.box(value.sourceMetrics.byteCount),
        "generatedClasses" -> Int.box(value.sourceMetrics.generatedClasses)
      ),
      "phases" -> arr(value.phases.map { phase =>
        obj(
          "name" -> phase.name,
          "elapsedNanos" -> Long.box(phase.elapsedNanos),
          "inputCount" -> Int.box(phase.inputCount),
          "outputCount" -> Int.box(phase.outputCount)
        )
      })
    )

  private def summaryObject(value: BenchmarkSummary): Object =
    obj(
      "medianNanos" -> Long.box(value.medianNanos),
      "p95Nanos" -> Long.box(value.p95Nanos),
      "minNanos" -> Long.box(value.minNanos),
      "maxNanos" -> Long.box(value.maxNanos)
    )

  private def failureObject(value: BenchmarkFailure): Object =
    obj(
      "category" -> value.category.wireName,
      "message" -> value.message,
      "iteration" -> value.iteration.map(Int.box).orNull
    )

  private def obj(entries: (String, Object)*): JMap[String, Object] =
    val result = new LinkedHashMap[String, Object]()
    entries.foreach { case (key, value) => result.put(key, value) }
    result

  private def arr(values: Seq[Object]): JList[Object] =
    val result = new ArrayList[Object]()
    values.foreach(result.add)
    result

  private def millis(nanos: Long): Double =
    nanos.toDouble / 1000000.0
```

- [ ] **Step 6: Run rendering tests**

Run:

```bash
sbt 'testOnly onion.tools.readiness.benchmark.BenchmarkRenderSpec'
```

Expected: 2 tests pass.

- [ ] **Step 7: Commit report metadata and rendering**

```bash
git add \
  src/main/scala/onion/tools/readiness/benchmark/BenchmarkMetadata.scala \
  src/main/scala/onion/tools/readiness/benchmark/BenchmarkReport.scala \
  src/main/scala/onion/tools/readiness/benchmark/BenchmarkRender.scala \
  src/test/scala/onion/tools/readiness/benchmark/BenchmarkRenderSpec.scala
git commit -m "Add versioned benchmark reports"
```

---

### Task 5: Compatibility CLI and sbt Tasks

**Files:**

- Create:
  `src/main/scala/onion/tools/readiness/benchmark/BenchmarkOptions.scala`
- Modify: `src/main/scala/onion/tools/BenchmarkRunner.scala`
- Modify: `build.sbt`
- Test:
  `src/test/scala/onion/tools/readiness/benchmark/BenchmarkOptionsSpec.scala`

**Interfaces:**

- Consumes: the engine, default catalog, metadata capture, report model, and
  renderers from Tasks 1–4.
- Produces:
  `BenchmarkOptions.parse`, `onion.tools.BenchmarkRunner`, `sbt benchmark`, and
  the retained `sbt bench` entry point.

- [ ] **Step 1: Write option compatibility and validation tests**

Create `BenchmarkOptionsSpec.scala`:

```scala
package onion.tools.readiness.benchmark

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.OptionValues

import java.nio.file.Paths

class BenchmarkOptionsSpec extends AnyFunSpec with Matchers with OptionValues:
  describe("BenchmarkOptions.parse"):
    it("uses readiness-safe defaults"):
      BenchmarkOptions.parse(Array.empty) shouldBe Right(
        BenchmarkOptions(
          runConfig = BenchmarkRunConfig(8, 25, 30000L),
          output = Paths.get("target/readiness/benchmark-v1.json"),
          stdoutFormat = BenchmarkOutputFormat.Text
        )
      )

    it("preserves --iterations and --json compatibility"):
      val parsed =
        BenchmarkOptions.parse(Array("--iterations", "3", "--json")).toOption.value
      parsed.runConfig.measuredIterations shouldBe 3
      parsed.stdoutFormat shouldBe BenchmarkOutputFormat.Json

    it("accepts explicit warmup, timeout, and output values"):
      val parsed = BenchmarkOptions.parse(
        Array(
          "--warmups", "2",
          "--timeout-seconds", "5",
          "--output", "target/custom.json",
          "--format", "text"
        )
      ).toOption.value
      parsed.runConfig shouldBe BenchmarkRunConfig(2, 25, 5000L)
      parsed.output shouldBe Paths.get("target/custom.json")

    it("rejects unknown and invalid options"):
      BenchmarkOptions.parse(Array("--iterations", "0")).left.toOption.value should include ("positive")
      BenchmarkOptions.parse(Array("--wat")).left.toOption.value should include ("unknown")
```

- [ ] **Step 2: Run the option suite and verify it fails**

Run:

```bash
sbt 'testOnly onion.tools.readiness.benchmark.BenchmarkOptionsSpec'
```

Expected: compilation fails because option types do not exist.

- [ ] **Step 3: Implement side-effect-free option parsing**

Create `BenchmarkOptions.scala`:

```scala
package onion.tools.readiness.benchmark

import java.nio.file.{Path, Paths}

enum BenchmarkOutputFormat:
  case Text
  case Json

final case class BenchmarkOptions(
  runConfig: BenchmarkRunConfig = BenchmarkRunConfig(),
  output: Path = Paths.get("target/readiness/benchmark-v1.json"),
  stdoutFormat: BenchmarkOutputFormat = BenchmarkOutputFormat.Text
)

object BenchmarkOptions:
  def parse(args: Array[String]): Either[String, BenchmarkOptions] =
    parseAt(args.toVector, 0, BenchmarkOptions())

  private def parseAt(
    args: Vector[String],
    index: Int,
    options: BenchmarkOptions
  ): Either[String, BenchmarkOptions] =
    if index >= args.size then Right(options)
    else
      args(index) match
        case "--iterations" =>
          value(args, index, "--iterations").flatMap { raw =>
            positiveInt(raw, "iterations").flatMap { count =>
              parseAt(
                args,
                index + 2,
                options.copy(
                  runConfig = options.runConfig.copy(measuredIterations = count)
                )
              )
            }
          }
        case "--warmups" =>
          value(args, index, "--warmups").flatMap { raw =>
            nonNegativeInt(raw, "warmups").flatMap { count =>
              parseAt(
                args,
                index + 2,
                options.copy(
                  runConfig = options.runConfig.copy(warmupIterations = count)
                )
              )
            }
          }
        case "--timeout-seconds" =>
          value(args, index, "--timeout-seconds").flatMap { raw =>
            positiveInt(raw, "timeout seconds").flatMap { seconds =>
              parseAt(
                args,
                index + 2,
                options.copy(
                  runConfig = options.runConfig.copy(
                    timeoutMillis = seconds.toLong * 1000L
                  )
                )
              )
            }
          }
        case "--output" =>
          value(args, index, "--output").flatMap { path =>
            parseAt(args, index + 2, options.copy(output = Paths.get(path)))
          }
        case "--format" =>
          value(args, index, "--format").flatMap {
            case "text" =>
              parseAt(
                args,
                index + 2,
                options.copy(stdoutFormat = BenchmarkOutputFormat.Text)
              )
            case "json" =>
              parseAt(
                args,
                index + 2,
                options.copy(stdoutFormat = BenchmarkOutputFormat.Json)
              )
            case other => Left(s"unsupported benchmark format: $other")
          }
        case "--json" =>
          parseAt(
            args,
            index + 1,
            options.copy(stdoutFormat = BenchmarkOutputFormat.Json)
          )
        case other => Left(s"unknown benchmark option: $other")

  private def value(
    args: Vector[String],
    index: Int,
    option: String
  ): Either[String, String] =
    args.lift(index + 1).toRight(s"missing value for $option")

  private def positiveInt(raw: String, label: String): Either[String, Int] =
    raw.toIntOption.filter(_ > 0).toRight(s"$label must be a positive integer")

  private def nonNegativeInt(raw: String, label: String): Either[String, Int] =
    raw.toIntOption.filter(_ >= 0).toRight(s"$label must be non-negative")
```

- [ ] **Step 4: Replace the old benchmark implementation with the thin CLI**

Replace `src/main/scala/onion/tools/BenchmarkRunner.scala` with:

```scala
package onion.tools

import onion.tools.readiness.benchmark.*

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.Executors

object BenchmarkRunner:
  def main(args: Array[String]): Unit =
    BenchmarkOptions.parse(args) match
      case Left(message) =>
        Console.err.println(s"benchmark: $message")
        sys.exit(2)
      case Right(options) =>
        val repoRoot = Paths.get("").toAbsolutePath.normalize()
        val report = buildReport(repoRoot, options)
        writeJson(options.output, BenchmarkRender.json(report))
        options.stdoutFormat match
          case BenchmarkOutputFormat.Text =>
            println(BenchmarkRender.text(report))
          case BenchmarkOutputFormat.Json =>
            println(BenchmarkRender.json(report))
        if !report.succeeded then sys.exit(1)

  private[onion] def buildReport(
    repoRoot: Path,
    options: BenchmarkOptions
  ): PerformanceBenchmarkReport =
    val (git, metadataFailures) =
      GitMetadata.capture(repoRoot) match
        case Right(value) => (value, Vector.empty)
        case Left(message) =>
          (
            GitMetadata("unknown", dirty = true),
            Vector(
              BenchmarkFailure(
                FailureCategory.InvalidMeasurement,
                message
              )
            )
          )
    val executor = Executors.newSingleThreadExecutor()
    val engine = new BenchmarkEngine(
      options.runConfig,
      NanoClock.System,
      executor
    )
    val results =
      try CompileScenarioCatalog.default(repoRoot).map(engine.run)
      finally engine.close()
    PerformanceBenchmarkReport.create(
      git = git,
      environment = EnvironmentMetadata.capture(),
      runConfig = options.runConfig,
      scenarios = results,
      failures = metadataFailures
    )

  private def writeJson(path: Path, content: String): Unit =
    val parent = path.toAbsolutePath.normalize().getParent
    if parent != null then Files.createDirectories(parent)
    Files.writeString(path, content, StandardCharsets.UTF_8)
```

- [ ] **Step 5: Add the clearer sbt task without removing `bench`**

In `build.sbt`, add the new key next to `bench`:

```scala
lazy val bench = inputKey[Unit]("Runs the benchmark suite (compatibility alias)")
lazy val benchmark = inputKey[Unit]("Runs the versioned readiness benchmark suite")
```

Keep the existing `bench` `fullRunInputTask` and add:

```scala
fullRunInputTask(
  benchmark,
  Compile,
  "onion.tools.BenchmarkRunner"
)
```

- [ ] **Step 6: Run option and benchmark component tests**

Run:

```bash
sbt 'testOnly onion.tools.readiness.benchmark.*'
```

Expected: all measurement-core tests pass.

- [ ] **Step 7: Exercise the one-iteration compatibility paths**

Run:

```bash
sbt 'bench --warmups 0 --iterations 1 --json --output target/readiness/bench-compat.json'
sbt 'benchmark --warmups 0 --iterations 1 --format text --output target/readiness/benchmark-v1.json'
```

Expected:

- both commands exit zero;
- both compile `Hello.on`, `TodoManager.on`, and `StatsApp.on`;
- the first prints schema-versioned JSON;
- the second prints three text summary lines; and
- both requested JSON files parse successfully and contain
  `"schemaVersion": 1`.

- [ ] **Step 8: Commit CLI and build integration**

```bash
git add \
  src/main/scala/onion/tools/readiness/benchmark/BenchmarkOptions.scala \
  src/main/scala/onion/tools/BenchmarkRunner.scala \
  src/test/scala/onion/tools/readiness/benchmark/BenchmarkOptionsSpec.scala \
  build.sbt
git commit -m "Replace benchmark runner with readiness measurements"
```

---

### Task 6: Contributor Documentation and Final Verification

**Files:**

- Modify: `docs/contributing/building.md`
- Modify: `docs/ja/contributing/building.md`

**Interfaces:**

- Consumes: `sbt benchmark`, `sbt bench`, the CLI flags, and the JSON artifact
  path from Task 5.
- Produces: matching English and Japanese instructions that do not call a
  JIT-warmed fresh compiler a warm compiler.

- [ ] **Step 1: Add the English benchmark contract**

Replace the short benchmark paragraph in
`docs/contributing/building.md` with:

````markdown
Run the readiness benchmark suite:

```bash
sbt benchmark
```

The default suite measures a fresh Onion compiler inside an already-warmed JVM
against `run/Hello.on`, `run/TodoManager.on`, and `run/StatsApp.on`. It reports
raw nanosecond observations, median and p95 latency, phase timings, source
metrics, and JVM/OS metadata. This is a **steady-state fresh-compiler**
measurement; it does not include JVM process startup and does not imply a
persistent compiler cache.

The machine-readable report is written to
`target/readiness/benchmark-v1.json`. For a quick protocol smoke test:

```bash
sbt 'benchmark --warmups 0 --iterations 1'
```

`sbt bench` remains a compatibility alias and accepts the same options.
````

- [ ] **Step 2: Add the equivalent Japanese contract**

Replace the corresponding paragraph in
`docs/ja/contributing/building.md` with:

````markdown
readiness ベンチマークスイートを実行します:

```bash
sbt benchmark
```

デフォルトのスイートは、すでにウォームアップされた JVM 内で毎回新しい
Onion コンパイラを作成し、`run/Hello.on`、`run/TodoManager.on`、
`run/StatsApp.on` をコンパイルします。生のナノ秒計測値、中央値、p95
レイテンシ、フェーズ別時間、ソース規模、JVM/OS メタデータを報告します。
これは **steady-state fresh-compiler** 計測です。JVM プロセスの起動時間は
含まず、永続コンパイラキャッシュがあることも意味しません。

機械可読レポートは `target/readiness/benchmark-v1.json` に書き出されます。
プロトコルだけを短時間で確認するには次を実行します:

```bash
sbt 'benchmark --warmups 0 --iterations 1'
```

`sbt bench` は互換エイリアスとして残り、同じオプションを受け付けます。
````

- [ ] **Step 3: Verify terminology and formatting**

Run:

```bash
rg -n 'cold-compile|warm-compile|DataClass.on|average-only' \
  src/main/scala/onion/tools/BenchmarkRunner.scala \
  src/main/scala/onion/tools/readiness/benchmark \
  docs/contributing/building.md \
  docs/ja/contributing/building.md
git diff --check
```

Expected: `rg` returns no matches and `git diff --check` reports no errors.

- [ ] **Step 4: Run focused and full verification**

Run:

```bash
sbt 'testOnly onion.tools.readiness.benchmark.*'
sbt test
sbt 'benchmark --warmups 0 --iterations 1 --output target/readiness/final-smoke.json'
```

Expected:

- every readiness benchmark test passes;
- the full test suite has zero failures and zero skipped tests;
- the smoke benchmark exits zero;
- `target/readiness/final-smoke.json` exists and parses as JSON; and
- all three scenario results contain one measured observation and a summary.

- [ ] **Step 5: Commit documentation**

```bash
git add docs/contributing/building.md docs/ja/contributing/building.md
git commit -m "Document readiness benchmark semantics"
```

---

## Completion Checkpoint

Do not start process-cold, persistent-REPL, multi-file, budget-enforcement, or
compiler-optimization work until this plan's evidence proves:

- raw observations and summaries coexist in the JSON report;
- warmups do not contribute to summaries;
- failed and timed-out iterations produce structured partial results;
- `StatsApp.on`, not `DataClass.on`, is the large practical workload;
- every scenario name says `steady-fresh`;
- both sbt entry points work;
- focused and full tests pass; and
- the English and Japanese build guides describe the same semantics.

After this checkpoint, write the follow-on performance-protocol plan using the
interfaces established here. That plan adds process-cold distribution timing,
a real persistent REPL driver, the deterministic 20-file workload, absolute
budgets, and base/head comparison.
