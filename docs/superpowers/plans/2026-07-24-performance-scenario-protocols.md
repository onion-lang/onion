# Performance Scenario Protocols Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend Onion's trustworthy measurement core with honest process-cold,
persistent-REPL, and deterministic 20-file compilation scenarios whose actual
per-scenario lifecycle is preserved in schema-versioned reports.

**Architecture:** `JvmRuntime` resolves a child-JVM command from loaded class
locations without assuming an sbt or distribution layout. Process-cold
iterations use a cancellable child-process launcher; persistent REPL
measurements drive one real `onion.tools.Repl` process through its plain prompt
protocol; the multi-file scenario reuses the compiler adapter against a
committed deterministic fixture. Scenario-specific warmup counts are resolved
by the runner and recorded in each result, requiring report schema version 2.

**Tech Stack:** Scala 3.3.7, Java 17 process/concurrency/NIO APIs, ScalaTest
3.2.19, Onion compiler and REPL, sbt 1.12.1.

## Global Constraints

- Use Scala 3 indentation style with two spaces and no tabs.
- Add no third-party dependencies.
- Store every duration as integer nanoseconds.
- Process-cold scenarios use 3 warmups by default; other scenarios use 8.
- Every scenario uses 25 measurements and a 30-second iteration timeout by
  default.
- `--warmups N` overrides every scenario's default, including process-cold.
- A process timeout or interruption must forcibly terminate its child process.
- Persistent-REPL setup is excluded from iteration timing and one real REPL
  process is retained for the complete scenario.
- The multi-file fixture contains exactly 20 Onion files and between 1,900 and
  2,200 lines.
- Preserve both `sbt bench` and `sbt benchmark`.
- Increment the performance report schema to 2 and record the effective
  `BenchmarkRunConfig` inside each scenario result.
- Benchmark fixture generation may use only the required JDK.

---

## File Map

### New production files

- `src/main/scala/onion/tools/readiness/benchmark/JvmRuntime.scala` resolves
  the current Java executable and complete child classpath.
- `src/main/scala/onion/tools/readiness/benchmark/ProcessSupport.scala` owns
  cancellable child-process execution and output capture.
- `src/main/scala/onion/tools/readiness/benchmark/ProcessColdScenario.scala`
  owns fresh-JVM script execution.
- `src/main/scala/onion/tools/readiness/benchmark/ReplProcess.scala` owns the
  real REPL prompt protocol.
- `src/main/scala/onion/tools/readiness/benchmark/PersistentReplScenario.scala`
  adapts a growing REPL session to `BenchmarkScenario`.
- `benchmarks/fixtures/automation-project/Generate.java` deterministically
  regenerates the 20-file fixture.
- `benchmarks/fixtures/automation-project/Stage01.on` through `Stage19.on`
  and `Pipeline.on` are generated and committed workload inputs.

### Modified production files

- `src/main/scala/onion/tools/readiness/benchmark/BenchmarkModel.scala`
- `src/main/scala/onion/tools/readiness/benchmark/BenchmarkEngine.scala`
- `src/main/scala/onion/tools/readiness/benchmark/BenchmarkOptions.scala`
- `src/main/scala/onion/tools/readiness/benchmark/BenchmarkReport.scala`
- `src/main/scala/onion/tools/readiness/benchmark/BenchmarkRender.scala`
- `src/main/scala/onion/tools/readiness/benchmark/FreshCompileScenario.scala`
- `src/main/scala/onion/tools/BenchmarkRunner.scala`
- `docs/contributing/building.md`
- `docs/ja/contributing/building.md`

### New tests

- `src/test/scala/onion/tools/readiness/benchmark/JvmRuntimeSpec.scala`
- `src/test/scala/onion/tools/readiness/benchmark/ProcessColdScenarioSpec.scala`
- `src/test/scala/onion/tools/readiness/benchmark/ReplProcessSpec.scala`
- `src/test/scala/onion/tools/readiness/benchmark/PersistentReplScenarioSpec.scala`

### Modified tests

- `src/test/scala/onion/tools/readiness/benchmark/BenchmarkEngineSpec.scala`
- `src/test/scala/onion/tools/readiness/benchmark/BenchmarkOptionsSpec.scala`
- `src/test/scala/onion/tools/readiness/benchmark/BenchmarkRenderSpec.scala`
- `src/test/scala/onion/tools/readiness/benchmark/FreshCompileScenarioSpec.scala`
- `src/test/scala/onion/tools/BenchmarkRunnerSpec.scala`

---

### Task 1: Scenario-Specific Configuration and Schema v2

**Files:**

- Modify:
  `src/main/scala/onion/tools/readiness/benchmark/BenchmarkModel.scala`
- Modify:
  `src/main/scala/onion/tools/readiness/benchmark/BenchmarkEngine.scala`
- Modify:
  `src/main/scala/onion/tools/readiness/benchmark/BenchmarkOptions.scala`
- Modify:
  `src/main/scala/onion/tools/readiness/benchmark/BenchmarkReport.scala`
- Modify:
  `src/main/scala/onion/tools/readiness/benchmark/BenchmarkRender.scala`
- Modify:
  `src/test/scala/onion/tools/readiness/benchmark/BenchmarkEngineSpec.scala`
- Modify:
  `src/test/scala/onion/tools/readiness/benchmark/BenchmarkOptionsSpec.scala`
- Modify:
  `src/test/scala/onion/tools/readiness/benchmark/BenchmarkRenderSpec.scala`

**Interfaces:**

- Consumes: `BenchmarkRunConfig`, `BenchmarkScenario`, `ScenarioResult`.
- Produces:
  `BenchmarkScenario.defaultWarmupIterations`,
  `BenchmarkOptions.warmupOverride`, and
  `ScenarioResult.runConfig`.

- [ ] **Step 1: Write failing effective-configuration tests**

Add to `BenchmarkEngineSpec.scala`:

```scala
    it("records the effective run configuration in the scenario result"):
      val scenario = new BenchmarkScenario:
        override val metadata: ScenarioMetadata = BenchmarkEngineSpec.this.metadata
        override def open(): BenchmarkSession = new BenchmarkSession:
          override def runIteration(index: Int): IterationPayload = payload

      val config = BenchmarkRunConfig(0, 1, 1000L)
      val executor = Executors.newSingleThreadExecutor()
      val engine = new BenchmarkEngine(
        config,
        new SequenceClock(Seq(0L, 1L)),
        executor
      )
      val result = try engine.run(scenario) finally engine.close()

      result.runConfig shouldBe config
```

Extend the explicit warmup test in `BenchmarkOptionsSpec.scala`:

```scala
      parsed.warmupOverride shouldBe Some(2)
```

Add to its default test:

```scala
      BenchmarkOptions.parse(Array.empty).toOption.value.warmupOverride shouldBe None
```

Extend `BenchmarkRenderSpec.scala`:

```scala
      json should include ("\"schemaVersion\": 2")
      json should include ("\"warmupIterations\": 0")
```

- [ ] **Step 2: Run the affected suites and verify RED**

Run:

```bash
sbt 'testOnly onion.tools.readiness.benchmark.BenchmarkEngineSpec onion.tools.readiness.benchmark.BenchmarkOptionsSpec onion.tools.readiness.benchmark.BenchmarkRenderSpec'
```

Expected: compilation fails because `ScenarioResult.runConfig` and
`BenchmarkOptions.warmupOverride` do not exist, and the schema expectation is
still version 1.

- [ ] **Step 3: Add effective configuration to scenario results**

In `BenchmarkModel.scala`, add `runConfig` immediately after `metadata`:

```scala
final case class ScenarioResult(
  metadata: ScenarioMetadata,
  runConfig: BenchmarkRunConfig,
  warmups: Vector[IterationObservation],
  measurements: Vector[IterationObservation],
  summary: Option[BenchmarkSummary],
  failure: Option[BenchmarkFailure]
):
  def succeeded: Boolean = failure.isEmpty
```

In `BenchmarkEngine.run`, pass the engine configuration:

```scala
    ScenarioResult(
      metadata = scenario.metadata,
      runConfig = config,
      warmups = keptWarmups,
      measurements = keptMeasurements,
      summary = summary,
      failure = finalFailure
    )
```

Add this default to `BenchmarkScenario`:

```scala
  def defaultWarmupIterations: Int = 8
```

Update every test construction of `ScenarioResult` with its effective
`BenchmarkRunConfig`.

- [ ] **Step 4: Preserve explicit warmup intent**

Add the final field to `BenchmarkOptions`:

```scala
  warmupOverride: Option[Int] = None
```

When parsing `--warmups`, set both values:

```scala
options.copy(
  runConfig = options.runConfig.copy(warmupIterations = count),
  warmupOverride = Some(count)
)
```

All other parsing paths retain the field unchanged.

- [ ] **Step 5: Render schema version 2 and per-scenario configs**

Set:

```scala
  val CurrentSchemaVersion = 2
```

In `BenchmarkRender.scenarioObject`, add:

```scala
      "runConfig" -> runConfigObject(result.runConfig),
```

Extract the existing top-level rendering into:

```scala
  private def runConfigObject(value: BenchmarkRunConfig): Object =
    obj(
      "warmupIterations" -> Int.box(value.warmupIterations),
      "measuredIterations" -> Int.box(value.measuredIterations),
      "timeoutMillis" -> Long.box(value.timeoutMillis)
    )
```

Use this helper for both the top-level and scenario fields. Update render test
fixtures and expectations from schema 1 to schema 2.

- [ ] **Step 6: Run the three suites and all readiness benchmark tests**

Run:

```bash
sbt 'testOnly onion.tools.readiness.benchmark.BenchmarkEngineSpec onion.tools.readiness.benchmark.BenchmarkOptionsSpec onion.tools.readiness.benchmark.BenchmarkRenderSpec'
sbt 'testOnly onion.tools.readiness.benchmark.* onion.tools.BenchmarkRunnerSpec'
```

Expected: all tests pass.

- [ ] **Step 7: Commit**

```bash
git add \
  src/main/scala/onion/tools/readiness/benchmark/BenchmarkModel.scala \
  src/main/scala/onion/tools/readiness/benchmark/BenchmarkEngine.scala \
  src/main/scala/onion/tools/readiness/benchmark/BenchmarkOptions.scala \
  src/main/scala/onion/tools/readiness/benchmark/BenchmarkReport.scala \
  src/main/scala/onion/tools/readiness/benchmark/BenchmarkRender.scala \
  src/test/scala/onion/tools/readiness/benchmark/BenchmarkEngineSpec.scala \
  src/test/scala/onion/tools/readiness/benchmark/BenchmarkOptionsSpec.scala \
  src/test/scala/onion/tools/readiness/benchmark/BenchmarkRenderSpec.scala
git commit -m "Record scenario benchmark configurations"
```

---

### Task 2: Child JVM and Cancellable Process Boundary

**Files:**

- Create:
  `src/main/scala/onion/tools/readiness/benchmark/JvmRuntime.scala`
- Create:
  `src/main/scala/onion/tools/readiness/benchmark/ProcessSupport.scala`
- Test:
  `src/test/scala/onion/tools/readiness/benchmark/JvmRuntimeSpec.scala`

**Interfaces:**

- Consumes: the loaded Onion, Scala, ASM, and JLine classes.
- Produces:
  `JvmRuntime.current`,
  `ProcessLauncher.run(command, workingDirectory, environment)`, and
  `ProcessOutcome`.

- [ ] **Step 1: Write failing JVM/process tests**

Create `JvmRuntimeSpec.scala`:

```scala
package onion.tools.readiness.benchmark

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.Files

class JvmRuntimeSpec extends AnyFunSpec with Matchers:
  describe("JvmRuntime.current"):
    it("resolves an executable Java command and the loaded Onion classpath"):
      val runtime = JvmRuntime.current()

      Files.isRegularFile(runtime.javaExecutable) shouldBe true
      runtime.classPath should include ("scala")
      runtime.classPath.split(java.io.File.pathSeparator).exists { entry =>
        entry.contains("classes") || entry.endsWith(".jar")
      } shouldBe true

  describe("ProcessLauncher.System"):
    it("captures stdout, stderr, and exit code"):
      val runtime = JvmRuntime.current()
      val workingDirectory = Files.createTempDirectory("onion-process-probe")
      val outcome = ProcessLauncher.System.run(
        Vector(
          runtime.javaExecutable.toString,
          "-version"
        ),
        workingDirectory,
        Map.empty
      )

      outcome.exitCode shouldBe 0
      (outcome.stdout + outcome.stderr).toLowerCase should include ("version")
```

- [ ] **Step 2: Run the suite and verify RED**

Run:

```bash
sbt 'testOnly onion.tools.readiness.benchmark.JvmRuntimeSpec'
```

Expected: compilation fails because runtime and process types do not exist.

- [ ] **Step 3: Implement current JVM discovery**

Create `JvmRuntime.scala`:

```scala
package onion.tools.readiness.benchmark

import onion.compiler.OnionCompiler
import org.jline.terminal.Terminal
import org.objectweb.asm.ClassReader
import org.objectweb.asm.commons.GeneratorAdapter
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.util.CheckClassAdapter

import java.io.File
import java.nio.file.{Files, Path, Paths}

final case class JvmRuntime(javaExecutable: Path, classPath: String)

object JvmRuntime:
  def current(): JvmRuntime =
    val executableName =
      if System.getProperty("os.name").toLowerCase.contains("win") then "java.exe"
      else "java"
    val executable =
      Paths.get(System.getProperty("java.home"), "bin", executableName)
        .toAbsolutePath
        .normalize()
    require(Files.isRegularFile(executable), s"Java executable not found: $executable")

    val classes = Vector(
      classOf[OnionCompiler],
      classOf[scala.Option[?]],
      classOf[scala.deriving.Mirror],
      classOf[ClassReader],
      classOf[GeneratorAdapter],
      classOf[ClassNode],
      classOf[Analyzer[?]],
      classOf[CheckClassAdapter],
      classOf[Terminal]
    )
    val entries = classes.flatMap(codeLocation).distinct
    require(entries.nonEmpty, "no child-JVM classpath entries were resolved")
    JvmRuntime(executable, entries.mkString(File.pathSeparator))

  private def codeLocation(klass: Class[?]): Option[String] =
    Option(klass.getProtectionDomain)
      .flatMap(domain => Option(domain.getCodeSource))
      .flatMap(source => Option(source.getLocation))
      .map(location => Paths.get(location.toURI).toAbsolutePath.normalize().toString)
```

- [ ] **Step 4: Implement cancellable process capture**

Create `ProcessSupport.scala`:

```scala
package onion.tools.readiness.benchmark

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.{Callable, Executors, Future}
import scala.jdk.CollectionConverters.*

final case class ProcessOutcome(
  exitCode: Int,
  stdout: String,
  stderr: String
)

trait ProcessLauncher:
  def run(
    command: Vector[String],
    workingDirectory: Path,
    environment: Map[String, String]
  ): ProcessOutcome

object ProcessLauncher:
  object System extends ProcessLauncher:
    override def run(
      command: Vector[String],
      workingDirectory: Path,
      environment: Map[String, String]
    ): ProcessOutcome =
      val builder = new ProcessBuilder(command*)
        .directory(workingDirectory.toFile)
      builder.environment().putAll(environment.asJava)
      val process = builder.start()
      val readers = Executors.newFixedThreadPool(2, runnable =>
        val thread = new Thread(runnable, "onion-process-output")
        thread.setDaemon(true)
        thread
      )
      val stdout = readAsync(readers, process.getInputStream)
      val stderr = readAsync(readers, process.getErrorStream)
      try
        val exitCode = process.waitFor()
        ProcessOutcome(exitCode, stdout.get(), stderr.get())
      catch
        case interrupted: InterruptedException =>
          process.destroyForcibly()
          process.waitFor()
          stdout.cancel(true)
          stderr.cancel(true)
          Thread.currentThread().interrupt()
          throw interrupted
      finally
        readers.shutdownNow()

    private def readAsync(
      readers: java.util.concurrent.ExecutorService,
      stream: java.io.InputStream
    ): Future[String] =
      readers.submit(new Callable[String]:
        override def call(): String =
          try new String(stream.readAllBytes(), StandardCharsets.UTF_8)
          finally stream.close()
      )
```

- [ ] **Step 5: Run the suite and focused benchmark tests**

Run:

```bash
sbt 'testOnly onion.tools.readiness.benchmark.JvmRuntimeSpec'
sbt 'testOnly onion.tools.readiness.benchmark.*'
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add \
  src/main/scala/onion/tools/readiness/benchmark/JvmRuntime.scala \
  src/main/scala/onion/tools/readiness/benchmark/ProcessSupport.scala \
  src/test/scala/onion/tools/readiness/benchmark/JvmRuntimeSpec.scala
git commit -m "Add child JVM benchmark support"
```

---

### Task 3: Honest Process-Cold Script Scenario

**Files:**

- Create:
  `src/main/scala/onion/tools/readiness/benchmark/ProcessColdScenario.scala`
- Create:
  `src/test/scala/onion/tools/readiness/benchmark/ProcessColdScenarioSpec.scala`
- Modify:
  `src/main/scala/onion/tools/readiness/benchmark/BenchmarkOptions.scala`
- Modify:
  `src/main/scala/onion/tools/BenchmarkRunner.scala`
- Modify:
  `src/test/scala/onion/tools/BenchmarkRunnerSpec.scala`

**Interfaces:**

- Consumes: `CompileWorkload`, `JvmRuntime`, `ProcessLauncher`,
  `BenchmarkScenario`, and `BenchmarkRunConfig`.
- Produces:
  `ProcessColdScenario` and
  `BenchmarkRunner.effectiveConfig(scenario, options)`.

- [ ] **Step 1: Write failing process lifecycle tests**

Create `ProcessColdScenarioSpec.scala`:

```scala
package onion.tools.readiness.benchmark

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.Executors

class ProcessColdScenarioSpec extends AnyFunSpec with Matchers:
  describe("ProcessColdScenario"):
    it("launches a new script-runner process for every iteration"):
      val root = Files.createTempDirectory("onion-process-cold")
      Files.writeString(root.resolve("Hello.on"), """IO::println("Hello")""")
      val workload =
        CompileWorkload.fromFiles(root, "hello", "Hello.on", Vector("Hello.on"))
      var launches = 0
      val launcher = new ProcessLauncher:
        override def run(
          command: Vector[String],
          workingDirectory: java.nio.file.Path,
          environment: Map[String, String]
        ): ProcessOutcome =
          launches += 1
          ProcessOutcome(0, "Hello\n", "")
      val runtime = JvmRuntime(root.resolve("java"), "runtime-classpath")
      val scenario =
        new ProcessColdScenario(workload, runtime, launcher, "Hello\n")
      val executor = Executors.newSingleThreadExecutor()
      val engine = new BenchmarkEngine(
        BenchmarkRunConfig(1, 2, 1000L),
        NanoClock.System,
        executor
      )

      val result = try engine.run(scenario) finally engine.close()

      launches shouldBe 3
      result.failure shouldBe None
      result.metadata.id shouldBe "process-cold:onion:hello"
      scenario.defaultWarmupIterations shouldBe 3

    it("fails when a successful process prints the wrong result"):
      val root = Files.createTempDirectory("onion-process-output")
      Files.writeString(root.resolve("Hello.on"), """IO::println("Hello")""")
      val workload =
        CompileWorkload.fromFiles(root, "hello", "Hello.on", Vector("Hello.on"))
      val launcher = new ProcessLauncher:
        override def run(
          command: Vector[String],
          workingDirectory: java.nio.file.Path,
          environment: Map[String, String]
        ): ProcessOutcome = ProcessOutcome(0, "wrong\n", "")
      val scenario = new ProcessColdScenario(
        workload,
        JvmRuntime(root.resolve("java"), "cp"),
        launcher,
        "Hello\n"
      )
      val session = scenario.open()

      val thrown = intercept[BenchmarkScenarioException] {
        session.runIteration(0)
      }
      thrown.getMessage should include ("unexpected stdout")
      session.close()
```

Add runner configuration tests to `BenchmarkRunnerSpec.scala`:

```scala
    it("uses three process-cold warmups unless the CLI overrides them"):
      val processScenario = new BenchmarkScenario:
        override val metadata =
          ScenarioMetadata("process", ScenarioKind.ProcessCold, "test", "hash")
        override def defaultWarmupIterations: Int = 3
        override def open(): BenchmarkSession =
          throw new UnsupportedOperationException()
      val defaults = BenchmarkOptions()
      BenchmarkRunner.effectiveConfig(processScenario, defaults).warmupIterations shouldBe 3
      val overrideOptions =
        BenchmarkOptions.parse(Array("--warmups", "0")).toOption.value
      BenchmarkRunner.effectiveConfig(
        processScenario,
        overrideOptions
      ).warmupIterations shouldBe 0
```

- [ ] **Step 2: Run the suites and verify RED**

Run:

```bash
sbt 'testOnly onion.tools.readiness.benchmark.ProcessColdScenarioSpec onion.tools.BenchmarkRunnerSpec'
```

Expected: compilation fails because the scenario and effective config do not
exist.

- [ ] **Step 3: Implement the process-cold scenario**

Create `ProcessColdScenario.scala`:

```scala
package onion.tools.readiness.benchmark

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

final class ProcessColdScenario(
  workload: CompileWorkload,
  runtime: JvmRuntime,
  launcher: ProcessLauncher,
  expectedStdout: String
) extends BenchmarkScenario:
  override val metadata: ScenarioMetadata =
    ScenarioMetadata(
      id = s"process-cold:onion:${workload.id}",
      kind = ScenarioKind.ProcessCold,
      workload = workload.label,
      workloadHash = workload.workloadHash
    )

  override def defaultWarmupIterations: Int = 3

  override def open(): BenchmarkSession =
    val workingDirectory = Files.createTempDirectory("onion-process-cold")
    new BenchmarkSession:
      override def runIteration(index: Int): IterationPayload =
        val outcome = launcher.run(
          Vector(
            runtime.javaExecutable.toString,
            "-cp",
            runtime.classPath,
            "onion.tools.ScriptRunner",
            workload.paths.head.toString
          ),
          workingDirectory,
          Map("TERM" -> "dumb")
        )
        if outcome.exitCode == 0 && normalize(outcome.stdout) != normalize(expectedStdout) then
          throw BenchmarkScenarioException(
            s"unexpected stdout for ${workload.label}: ${outcome.stdout.trim}"
          )
        IterationPayload(
          phases = Vector.empty,
          sourceMetrics = SourceMetrics(
            workload.sourceCount,
            workload.lineCount,
            workload.byteCount,
            generatedClasses = 0
          ),
          exitCode = outcome.exitCode
        )

      override def close(): Unit =
        if Files.exists(workingDirectory) then
          val entries = Files.walk(workingDirectory)
          try entries.iterator().asScala.toVector.reverse.foreach(Files.deleteIfExists)
          finally entries.close()

  private def normalize(value: String): String =
    value.replace("\r\n", "\n")
```

- [ ] **Step 4: Resolve and record each scenario's actual config**

Add to `BenchmarkRunner`:

```scala
  private[onion] def effectiveConfig(
    scenario: BenchmarkScenario,
    options: BenchmarkOptions
  ): BenchmarkRunConfig =
    options.runConfig.copy(
      warmupIterations =
        options.warmupOverride.getOrElse(scenario.defaultWarmupIterations)
    )
```

Construct each engine with `effectiveConfig(scenario, options)`, retaining one
daemon executor per scenario.

- [ ] **Step 5: Run the suites and a real one-iteration process scenario**

Run:

```bash
sbt 'testOnly onion.tools.readiness.benchmark.ProcessColdScenarioSpec onion.tools.BenchmarkRunnerSpec'
```

Then temporarily instantiate the real scenario in its test:

```scala
    it("runs Hello through a real fresh child JVM"):
      val repoRoot = java.nio.file.Paths.get(".").toAbsolutePath.normalize()
      val workload = CompileWorkload.fromFiles(
        repoRoot,
        "hello",
        "run/Hello.on",
        Vector("run/Hello.on")
      )
      val scenario = new ProcessColdScenario(
        workload,
        JvmRuntime.current(),
        ProcessLauncher.System,
        "Hello\n"
      )
      val executor = Executors.newSingleThreadExecutor()
      val engine = new BenchmarkEngine(
        BenchmarkRunConfig(0, 1, 30000L),
        NanoClock.System,
        executor
      )
      val result = try engine.run(scenario) finally engine.close()
      result.failure shouldBe None
```

Run the suite again. Expected: all three tests pass and the integration case
starts a real JVM.

- [ ] **Step 6: Commit**

```bash
git add \
  src/main/scala/onion/tools/readiness/benchmark/ProcessColdScenario.scala \
  src/main/scala/onion/tools/readiness/benchmark/BenchmarkOptions.scala \
  src/main/scala/onion/tools/BenchmarkRunner.scala \
  src/test/scala/onion/tools/readiness/benchmark/ProcessColdScenarioSpec.scala \
  src/test/scala/onion/tools/BenchmarkRunnerSpec.scala
git commit -m "Add process-cold benchmark scenario"
```

---

### Task 4: Real Persistent REPL Protocol

**Files:**

- Create:
  `src/main/scala/onion/tools/readiness/benchmark/ReplProcess.scala`
- Create:
  `src/main/scala/onion/tools/readiness/benchmark/PersistentReplScenario.scala`
- Test:
  `src/test/scala/onion/tools/readiness/benchmark/ReplProcessSpec.scala`
- Test:
  `src/test/scala/onion/tools/readiness/benchmark/PersistentReplScenarioSpec.scala`

**Interfaces:**

- Consumes: `JvmRuntime`, `BenchmarkScenario`, `BenchmarkSession`.
- Produces:
  `ReplClient`, `ReplClientFactory`, `ProcessReplClient`, and
  `PersistentReplScenario`.

- [ ] **Step 1: Write failing protocol and lifecycle tests**

Create `PersistentReplScenarioSpec.scala`:

```scala
package onion.tools.readiness.benchmark

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class PersistentReplScenarioSpec extends AnyFunSpec with Matchers:
  describe("PersistentReplScenario"):
    it("performs setup once and submits growing-state expressions"):
      val submitted = Vector.newBuilder[String]
      var closes = 0
      val factory = new ReplClientFactory:
        override def open(): ReplClient = new ReplClient:
          override def submit(code: String): String =
            submitted += code
            if code.startsWith("val baseline") then ""
            else s"res: Int = ${40 + code.split(\" \").last.toInt}"
          override def close(): Unit = closes += 1
      val scenario = new PersistentReplScenario(factory, "hash")
      val session = scenario.open()

      val first = session.runIteration(0)
      val second = session.runIteration(1)
      session.close()

      submitted.result() shouldBe Vector(
        "val baseline = 40",
        "baseline + 1",
        "baseline + 2"
      )
      first.exitCode shouldBe 0
      second.sourceMetrics.lineCount shouldBe 3
      closes shouldBe 1
      scenario.metadata.kind shouldBe ScenarioKind.PersistentSession
```

Create `ReplProcessSpec.scala`:

```scala
package onion.tools.readiness.benchmark

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.Files

class ReplProcessSpec extends AnyFunSpec with Matchers:
  describe("ProcessReplClient"):
    it("retains state across submissions to the real Onion REPL"):
      val workingDirectory = Files.createTempDirectory("onion-repl-process")
      val client = ProcessReplClient.start(
        JvmRuntime.current(),
        workingDirectory,
        timeoutMillis = 30000L
      )
      try
        client.submit("val baseline = 40")
        val output = client.submit("baseline + 2")
        output should include ("res0: Int = 42")
      finally client.close()
```

- [ ] **Step 2: Run both suites and verify RED**

Run:

```bash
sbt 'testOnly onion.tools.readiness.benchmark.PersistentReplScenarioSpec onion.tools.readiness.benchmark.ReplProcessSpec'
```

Expected: compilation fails because the REPL client interfaces do not exist.

- [ ] **Step 3: Implement the prompt-driven real REPL client**

Create `ReplProcess.scala`:

```scala
package onion.tools.readiness.benchmark

import java.io.{BufferedWriter, InputStream, OutputStreamWriter}
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}
import java.util.concurrent.atomic.AtomicReference

trait ReplClient extends AutoCloseable:
  def submit(code: String): String

trait ReplClientFactory:
  def open(): ReplClient

final class ProcessReplClient private (
  process: Process,
  input: BufferedWriter,
  stdout: ReplOutputPump,
  timeoutMillis: Long
) extends ReplClient:
  override def submit(code: String): String =
    try
      input.write(code)
      input.newLine()
      input.flush()
      stdout.readUntil("onion> ", timeoutMillis)
    catch
      case interrupted: InterruptedException =>
        process.destroyForcibly()
        Thread.currentThread().interrupt()
        throw interrupted

  override def close(): Unit =
    if process.isAlive then
      try
        input.write(":quit")
        input.newLine()
        input.flush()
        if !process.waitFor(1L, TimeUnit.SECONDS) then process.destroyForcibly()
      catch
        case _: Exception => process.destroyForcibly()
    input.close()

object ProcessReplClient:
  def start(
    runtime: JvmRuntime,
    workingDirectory: Path,
    timeoutMillis: Long
  ): ProcessReplClient =
    val process =
      new ProcessBuilder(
        runtime.javaExecutable.toString,
        "-cp",
        runtime.classPath,
        "onion.tools.Repl"
      )
        .directory(workingDirectory.toFile)
        .start()
    process.info()
    val stderr = new ReplOutputPump(process.getErrorStream, "repl-stderr")
    stderr.start()
    val stdout = new ReplOutputPump(process.getInputStream, "repl-stdout")
    stdout.start()
    val writer =
      new BufferedWriter(
        new OutputStreamWriter(process.getOutputStream, StandardCharsets.UTF_8)
      )
    val client = new ProcessReplClient(process, writer, stdout, timeoutMillis)
    try
      stdout.readUntil("onion> ", timeoutMillis)
      client
    catch
      case error: Throwable =>
        client.close()
        throw error

private final class ReplOutputPump(stream: InputStream, name: String):
  private val End = -1
  private val queue = new LinkedBlockingQueue[Int]()
  private val failure = new AtomicReference[Throwable]()
  private val thread = new Thread(
    () =>
      try
        var next = stream.read()
        while next >= 0 do
          queue.put(next)
          next = stream.read()
      catch case error: Throwable => failure.set(error)
      finally
        queue.offer(End)
        stream.close(),
    s"onion-$name"
  )
  thread.setDaemon(true)

  def start(): Unit = thread.start()

  def readUntil(marker: String, timeoutMillis: Long): String =
    val expected = marker.getBytes(StandardCharsets.UTF_8)
    val bytes = scala.collection.mutable.ArrayBuffer.empty[Byte]
    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
    while !endsWith(bytes, expected) do
      val remaining = deadline - System.nanoTime()
      if remaining <= 0L then
        throw BenchmarkScenarioException(s"REPL prompt timed out after $timeoutMillis ms")
      val next = queue.poll(remaining, TimeUnit.NANOSECONDS)
      if next == null then
        throw BenchmarkScenarioException(s"REPL prompt timed out after $timeoutMillis ms")
      if next == End then
        val cause = failure.get()
        val detail =
          if cause == null then "end of stream"
          else Option(cause.getMessage).getOrElse(cause.getClass.getSimpleName)
        throw BenchmarkScenarioException(s"REPL exited before prompt: $detail")
      bytes += next.toByte
    new String(bytes.toArray, StandardCharsets.UTF_8)

  private def endsWith(
    bytes: scala.collection.mutable.ArrayBuffer[Byte],
    expected: Array[Byte]
  ): Boolean =
    bytes.length >= expected.length &&
      expected.indices.forall { index =>
        bytes(bytes.length - expected.length + index) == expected(index)
      }
```

- [ ] **Step 4: Implement the persistent scenario**

Create `PersistentReplScenario.scala`:

```scala
package onion.tools.readiness.benchmark

import java.nio.charset.StandardCharsets

final class PersistentReplScenario(
  factory: ReplClientFactory,
  workloadHash: String
) extends BenchmarkScenario:
  override val metadata =
    ScenarioMetadata(
      "persistent-session:repl:growing-state",
      ScenarioKind.PersistentSession,
      "generated growing REPL session",
      workloadHash
    )

  override def open(): BenchmarkSession =
    val client = factory.open()
    client.submit("val baseline = 40")
    new BenchmarkSession:
      private var submission = 0
      private var bytes = "val baseline = 40".getBytes(StandardCharsets.UTF_8).length

      override def runIteration(index: Int): IterationPayload =
        submission += 1
        val code = s"baseline + $submission"
        bytes += code.getBytes(StandardCharsets.UTF_8).length
        val output = client.submit(code)
        val expected = 40 + submission
        if !output.contains(s"= $expected") then
          throw BenchmarkScenarioException(
            s"REPL returned an unexpected result for '$code': ${output.trim}"
          )
        IterationPayload(
          Vector.empty,
          SourceMetrics(1, submission + 1, bytes, generatedClasses = 1),
          exitCode = 0
        )

      override def close(): Unit = client.close()
```

The production factory in the catalog computes a SHA-256 hash of the literal
protocol version string `onion-repl-growing-state-v1`, creates a temporary
working directory, and starts `ProcessReplClient` with the scenario timeout.

- [ ] **Step 5: Run unit and real protocol tests**

Run:

```bash
sbt 'testOnly onion.tools.readiness.benchmark.PersistentReplScenarioSpec onion.tools.readiness.benchmark.ReplProcessSpec'
sbt 'testOnly onion.tools.readiness.benchmark.*'
```

Expected: both lifecycle and real child-REPL tests pass.

- [ ] **Step 6: Commit**

```bash
git add \
  src/main/scala/onion/tools/readiness/benchmark/ReplProcess.scala \
  src/main/scala/onion/tools/readiness/benchmark/PersistentReplScenario.scala \
  src/test/scala/onion/tools/readiness/benchmark/ReplProcessSpec.scala \
  src/test/scala/onion/tools/readiness/benchmark/PersistentReplScenarioSpec.scala
git commit -m "Add persistent REPL benchmark protocol"
```

---

### Task 5: Deterministic 20-File Workload and Complete Catalog

**Files:**

- Create:
  `benchmarks/fixtures/automation-project/Generate.java`
- Generate and create:
  `benchmarks/fixtures/automation-project/Stage01.on` through `Stage19.on`
- Generate and create:
  `benchmarks/fixtures/automation-project/Pipeline.on`
- Modify:
  `src/main/scala/onion/tools/readiness/benchmark/FreshCompileScenario.scala`
- Modify:
  `src/test/scala/onion/tools/readiness/benchmark/FreshCompileScenarioSpec.scala`

**Interfaces:**

- Consumes: `CompileWorkload`, `FreshCompileScenario`, `JvmRuntime`.
- Produces:
  `CompileScenarioCatalog.default(repoRoot, runtime, timeoutMillis)` containing
  six scenarios.

- [ ] **Step 1: Write failing fixture and catalog tests**

Replace the catalog test with:

```scala
    it("contains every honest practical performance protocol"):
      val root = Paths.get(".").toAbsolutePath.normalize()
      val scenarios =
        CompileScenarioCatalog.default(root, JvmRuntime.current(), 30000L)
      scenarios.map(_.metadata.id) shouldBe Vector(
        "steady-fresh:onionc:hello",
        "steady-fresh:onionc:todo-manager",
        "steady-fresh:onionc:stats-app",
        "process-cold:onion:hello",
        "persistent-session:repl:growing-state",
        "multi-file:onionc:automation-project"
      )
      scenarios.last.metadata.kind shouldBe ScenarioKind.MultiFile
      scenarios.last.metadata.workload shouldBe
        "benchmarks/fixtures/automation-project"
```

Add:

```scala
    it("uses a deterministic 20-file workload of approximately 2,000 lines"):
      val root = Paths.get(".").toAbsolutePath.normalize()
      val relativeFiles =
        (1 to 19).map(index => f"benchmarks/fixtures/automation-project/Stage$index%02d.on").toVector :+
          "benchmarks/fixtures/automation-project/Pipeline.on"
      val workload = CompileWorkload.fromFiles(
        root,
        "automation-project",
        "benchmarks/fixtures/automation-project",
        relativeFiles
      )
      workload.sourceCount shouldBe 20
      workload.lineCount should (be >= 1900 and be <= 2200)
```

- [ ] **Step 2: Run the suite and verify RED**

Run:

```bash
sbt 'testOnly onion.tools.readiness.benchmark.FreshCompileScenarioSpec'
```

Expected: compilation fails on the new catalog signature or the fixture is
missing.

- [ ] **Step 3: Add the deterministic JDK-only generator**

Create `benchmarks/fixtures/automation-project/Generate.java`:

```java
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Generate {
    private Generate() {}

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length == 0 ? "." : args[0])
            .toAbsolutePath()
            .normalize();
        Files.createDirectories(root);
        for (int stage = 1; stage <= 19; stage++) {
            String className = String.format("Stage%02d", stage);
            StringBuilder source = new StringBuilder();
            source.append("module readiness.automation\n\n");
            source.append("class ").append(className).append(" {\n");
            source.append("public:\n");
            for (int method = 1; method <= 32; method++) {
                source.append("  def step")
                    .append(String.format("%02d", method))
                    .append("(value: Int): Int {\n");
                source.append("    return value + ")
                    .append(stage + method)
                    .append("\n");
                source.append("  }\n");
            }
            source.append("}\n");
            Files.writeString(
                root.resolve(className + ".on"),
                source,
                StandardCharsets.UTF_8
            );
        }

        StringBuilder pipeline = new StringBuilder();
        pipeline.append("module readiness.automation\n\n");
        pipeline.append("class AutomationPipeline {\npublic:\n");
        pipeline.append("  def run(value: Int): Int {\n");
        pipeline.append("    var current = value\n");
        for (int stage = 1; stage <= 19; stage++) {
            pipeline.append("    current = new ")
                .append(String.format("Stage%02d", stage))
                .append("().step01(current)\n");
        }
        pipeline.append("    return current\n  }\n");
        for (int method = 1; method <= 24; method++) {
            pipeline.append("  def normalize")
                .append(String.format("%02d", method))
                .append("(value: Int): Int {\n");
            pipeline.append("    return value - ")
                .append(method)
                .append("\n");
            pipeline.append("  }\n");
        }
        pipeline.append("}\n");
        Files.writeString(
            root.resolve("Pipeline.on"),
            pipeline,
            StandardCharsets.UTF_8
        );
    }
}
```

Generate the committed inputs:

```bash
java benchmarks/fixtures/automation-project/Generate.java \
  benchmarks/fixtures/automation-project
```

- [ ] **Step 4: Generalize the compiler adapter**

Add optional constructor fields to `FreshCompileScenario`:

```scala
  scenarioKind: ScenarioKind = ScenarioKind.SteadyFresh,
  driver: String = "onionc"
```

Construct metadata with:

```scala
      id = s"${scenarioKind.wireName}:$driver:${workload.id}",
      kind = scenarioKind,
```

- [ ] **Step 5: Assemble the complete catalog**

Change the catalog signature to:

```scala
  def default(
    repoRoot: java.nio.file.Path,
    runtime: JvmRuntime,
    timeoutMillis: Long
  ): Vector[BenchmarkScenario]
```

Retain the three steady-fresh scenarios, then append:

```scala
      new ProcessColdScenario(
        helloWorkload,
        runtime,
        ProcessLauncher.System,
        "Hello\n"
      ),
      new PersistentReplScenario(
        new ReplClientFactory:
          override def open(): ReplClient =
            ProcessReplClient.start(
              runtime,
              Files.createTempDirectory("onion-repl-benchmark"),
              timeoutMillis
            ),
        sha256("onion-repl-growing-state-v1")
      ),
      new FreshCompileScenario(
        multiFileWorkload,
        config,
        ScenarioKind.MultiFile,
        "onionc"
      )
```

Factor SHA-256 string rendering into a package-private helper shared with
`CompileWorkload`.

- [ ] **Step 6: Compile the fixture and run catalog tests**

Run:

```bash
sbt 'testOnly onion.tools.readiness.benchmark.FreshCompileScenarioSpec'
sbt 'testOnly onion.tools.readiness.benchmark.*'
```

Expected: the fixture compiles, contains 20 files and 1,900–2,200 lines, and
the six catalog IDs match exactly.

- [ ] **Step 7: Commit**

```bash
git add \
  benchmarks/fixtures/automation-project \
  src/main/scala/onion/tools/readiness/benchmark/FreshCompileScenario.scala \
  src/test/scala/onion/tools/readiness/benchmark/FreshCompileScenarioSpec.scala
git commit -m "Add complete practical benchmark catalog"
```

---

### Task 6: CLI Integration, Documentation, and Full Verification

**Files:**

- Modify: `src/main/scala/onion/tools/BenchmarkRunner.scala`
- Modify:
  `src/test/scala/onion/tools/BenchmarkRunnerSpec.scala`
- Modify: `docs/contributing/building.md`
- Modify: `docs/ja/contributing/building.md`

**Interfaces:**

- Consumes: schema v2, `JvmRuntime`, the complete catalog, scenario-effective
  configs, and all renderers.
- Produces: a six-scenario schema-v2 report from both sbt entry points.

- [ ] **Step 1: Wire the complete catalog and effective configs**

In `BenchmarkRunner.buildReport`, capture `JvmRuntime.current()` inside the
existing setup-failure boundary and call:

```scala
CompileScenarioCatalog.default(
  repoRoot,
  runtime,
  options.runConfig.timeoutMillis
)
```

Construct each engine with:

```scala
effectiveConfig(scenario, options)
```

The setup boundary must still return a structured `InvalidMeasurement` report
when runtime discovery, fixture loading, or catalog construction fails.

- [ ] **Step 2: Extend runner report tests**

Add a catalog injection boundary:

```scala
private[onion] def runScenarios(
  scenarios: Vector[BenchmarkScenario],
  options: BenchmarkOptions
): Vector[ScenarioResult]
```

Test that a fake process-cold scenario receives 3 default warmups and a fake
steady scenario receives 8, while `--warmups 0` gives both zero. Assert the
result's recorded `runConfig` for every case.

- [ ] **Step 3: Update English documentation**

Replace the single-protocol description with:

````markdown
The default suite reports six explicit protocols:

- steady-state fresh compiler measurements for `run/Hello.on`,
  `run/TodoManager.on`, and `run/StatsApp.on`;
- a process-cold `onion run/Hello.on` measurement that includes child-JVM
  startup and shutdown;
- submissions to one persistent, growing Onion REPL session; and
- one compilation of the deterministic 20-file automation fixture under
  `benchmarks/fixtures/automation-project/`.

Process-cold uses 3 warmups by default; the other protocols use 8. Every
protocol uses 25 measured iterations and a 30-second iteration timeout.
`--warmups N` overrides the scenario defaults. The schema-v2 JSON report stores
the effective configuration beside every scenario so unlike lifecycles are
never presented as identical measurements.
````

- [ ] **Step 4: Add the equivalent Japanese documentation**

Use:

````markdown
デフォルトスイートは、次の6つの明示的なプロトコルを報告します:

- `run/Hello.on`、`run/TodoManager.on`、`run/StatsApp.on` に対する
  steady-state fresh compiler 計測
- 子 JVM の起動と終了を含む process-cold の
  `onion run/Hello.on` 計測
- 1つの永続的かつ状態が増加する Onion REPL セッションへの連続入力
- `benchmarks/fixtures/automation-project/` にある決定的な20ファイル
  automation fixture の一括コンパイル

process-cold のデフォルト warmup は3回、その他は8回です。全プロトコルを
25回計測し、1 iteration の timeout は30秒です。`--warmups N` は各
scenario のデフォルトを上書きします。schema-v2 JSON は有効な設定を
scenario ごとに保存するため、異なる lifecycle を同一条件の計測として
扱いません。
````

- [ ] **Step 5: Run both one-iteration CLI paths**

Run:

```bash
sbt 'bench --warmups 0 --iterations 1 --json --output target/readiness/protocol-compat-v2.json'
sbt 'benchmark --warmups 0 --iterations 1 --format text --output target/readiness/protocol-smoke-v2.json'
```

Expected:

- both commands exit zero;
- both reports have `schemaVersion` 2;
- each report contains exactly six scenario IDs;
- every scenario has one measurement, an effective run config, and a summary;
- process-cold starts a child JVM;
- the REPL result retains setup state; and
- the multi-file observation reports exactly 20 sources.

- [ ] **Step 6: Validate the report artifacts**

Run:

```bash
ruby -rjson -e '
ARGV.each do |path|
  report = JSON.parse(File.read(path))
  abort("#{path}: schema") unless report["schemaVersion"] == 2
  abort("#{path}: scenarios") unless report["scenarios"].length == 6
  report["scenarios"].each do |scenario|
    abort("#{path}: config") unless scenario["runConfig"]
    abort("#{path}: measurement") unless scenario["measurements"].length == 1
    abort("#{path}: summary") unless scenario["summary"]
  end
end
' target/readiness/protocol-compat-v2.json \
  target/readiness/protocol-smoke-v2.json
```

- [ ] **Step 7: Run focused and full verification**

Run:

```bash
sbt 'testOnly onion.tools.readiness.benchmark.* onion.tools.BenchmarkRunnerSpec'
sbt test
git diff --check
```

Expected: zero failed, canceled, ignored, pending, or skipped tests, and no
whitespace errors.

- [ ] **Step 8: Commit**

```bash
git add \
  src/main/scala/onion/tools/BenchmarkRunner.scala \
  src/test/scala/onion/tools/BenchmarkRunnerSpec.scala \
  docs/contributing/building.md \
  docs/ja/contributing/building.md
git commit -m "Document complete benchmark protocols"
```

---

## Completion Checkpoint

Do not implement absolute budgets or base/head comparisons until this plan
proves:

- report schema 2 records the effective run config for every scenario;
- process-cold iterations launch a new child JVM and have 3 default warmups;
- child processes are destroyed on interruption;
- persistent REPL setup is excluded and measurements share one real process;
- the multi-file fixture is deterministic, exactly 20 files, and approximately
  2,000 lines;
- both sbt entry points emit six successful scenario results;
- English and Japanese documentation name the same lifecycles; and
- the focused and full suites pass.

The next performance plan adds reference-lane detection, absolute budgets,
compatible base/head comparison, confirmation rounds, and the first captured
baseline.
