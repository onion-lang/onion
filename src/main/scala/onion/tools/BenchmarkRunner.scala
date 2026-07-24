package onion.tools

import onion.tools.readiness.benchmark.*

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.util.control.NonFatal

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
    val (scenarios, setupFailures) =
      try
        val runtime = JvmRuntime.current()
        (
          CompileScenarioCatalog.default(
            repoRoot,
            runtime,
            options.runConfig.timeoutMillis
          ),
          Vector.empty
        )
      catch
        case NonFatal(error) =>
          (
            Vector.empty,
            Vector(
              BenchmarkFailure(
                FailureCategory.InvalidMeasurement,
                Option(error.getMessage)
                  .filter(_.nonEmpty)
                  .getOrElse(error.getClass.getSimpleName)
              )
            )
          )
    val results = runScenarios(scenarios, options)
    PerformanceBenchmarkReport.create(
      git = git,
      environment = EnvironmentMetadata.capture(),
      runConfig = options.runConfig,
      scenarios = results,
      failures = metadataFailures ++ setupFailures
    )

  private[onion] def runScenarios(
    scenarios: Vector[BenchmarkScenario],
    options: BenchmarkOptions
  ): Vector[ScenarioResult] =
    scenarios.map { scenario =>
      val engine = new BenchmarkEngine(
        effectiveConfig(scenario, options),
        NanoClock.System,
        BenchmarkExecutor.daemonSingleThread()
      )
      try engine.run(scenario)
      finally engine.close()
    }

  private[onion] def effectiveConfig(
    scenario: BenchmarkScenario,
    options: BenchmarkOptions
  ): BenchmarkRunConfig =
    options.runConfig.copy(
      warmupIterations =
        options.warmupOverride.getOrElse(scenario.defaultWarmupIterations)
    )

  private def writeJson(path: Path, content: String): Unit =
    val parent = path.toAbsolutePath.normalize().getParent
    if parent != null then Files.createDirectories(parent)
    Files.writeString(path, content, StandardCharsets.UTF_8)
