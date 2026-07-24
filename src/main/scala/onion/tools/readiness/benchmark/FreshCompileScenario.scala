package onion.tools.readiness.benchmark

import onion.compiler.{
  CompilerConfig,
  FileInputSource,
  OnionCompiler,
  WarningLevel
}
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import scala.jdk.CollectionConverters.*

final class FreshCompileScenario(
  workload: CompileWorkload,
  compilerConfig: CompilerConfig,
  scenarioKind: ScenarioKind = ScenarioKind.SteadyFresh,
  driver: String = "onionc"
) extends BenchmarkScenario:
  override val metadata: ScenarioMetadata =
    ScenarioMetadata(
      id = s"${scenarioKind.wireName}:$driver:${workload.id}",
      kind = scenarioKind,
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
    default(repoRoot, JvmRuntime.current(), 30000L)

  def default(
    repoRoot: java.nio.file.Path,
    runtime: JvmRuntime,
    timeoutMillis: Long
  ): Vector[BenchmarkScenario] =
    val config = CompilerConfig(
      classPath = Seq("."),
      superClass = "",
      encoding = "UTF-8",
      outputDirectory = "",
      maxErrorReports = 20,
      warningLevel = WarningLevel.Off
    )
    val hello = CompileWorkload.fromFiles(
      repoRoot,
      "hello",
      "run/Hello.on",
      Vector("run/Hello.on")
    )
    val todoManager = CompileWorkload.fromFiles(
      repoRoot,
      "todo-manager",
      "run/TodoManager.on",
      Vector("run/TodoManager.on")
    )
    val statsApp = CompileWorkload.fromFiles(
      repoRoot,
      "stats-app",
      "run/StatsApp.on",
      Vector("run/StatsApp.on")
    )
    val multiFilePaths =
      (1 to 19).map { index =>
        f"benchmarks/fixtures/automation-project/Stage$index%02d.on"
      }.toVector :+
        "benchmarks/fixtures/automation-project/Pipeline.on"
    val multiFile = CompileWorkload.fromFiles(
      repoRoot,
      "automation-project",
      "benchmarks/fixtures/automation-project",
      multiFilePaths
    )
    Vector(
      new FreshCompileScenario(hello, config),
      new FreshCompileScenario(todoManager, config),
      new FreshCompileScenario(statsApp, config),
      new ProcessColdScenario(
        hello,
        runtime,
        ProcessLauncher.System,
        "Hello\n"
      ),
      new PersistentReplScenario(
        replFactory(runtime, timeoutMillis),
        hashText("onion-repl-growing-state-v1")
      ),
      new FreshCompileScenario(
        multiFile,
        config,
        ScenarioKind.MultiFile,
        "onionc"
      )
    )

  private def replFactory(
    runtime: JvmRuntime,
    timeoutMillis: Long
  ): ReplClientFactory =
    new ReplClientFactory:
      override def open(): ReplClient =
        val workingDirectory =
          Files.createTempDirectory("onion-repl-benchmark")
        val delegate =
          ProcessReplClient.start(runtime, workingDirectory, timeoutMillis)
        new ReplClient:
          override def submit(code: String): String = delegate.submit(code)

          override def close(): Unit =
            try delegate.close()
            finally deleteRecursively(workingDirectory)

  private def deleteRecursively(root: java.nio.file.Path): Unit =
    if Files.exists(root) then
      val entries = Files.walk(root)
      try
        entries.iterator().asScala.toVector.reverse.foreach { entry =>
          Files.deleteIfExists(entry)
        }
      finally entries.close()

  private def hashText(value: String): String =
    MessageDigest.getInstance("SHA-256")
      .digest(value.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x")
      .mkString
